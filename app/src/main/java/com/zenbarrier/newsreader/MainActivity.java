package com.zenbarrier.newsreader;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    SQLiteDatabase myDatabase;
    ListView listView;
    ArrayList<StoryData> stories;
    ArrayAdapter<StoryData> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = (ListView) findViewById(R.id.listView);
        stories = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, stories);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                StoryData story = stories.get(position);
                if(story.hasMissingData()){
                    Snackbar.make(findViewById(R.id.activity_main),"Loading Data",Snackbar.LENGTH_SHORT).show();
                }
                else{
                    Intent intent = new Intent(MainActivity.this, WebActivity.class);
                    intent.putExtra("url",story.url);
                    startActivity(intent);
                }
            }
        });

        try {
            myDatabase = this.openOrCreateDatabase("hackerNews", MODE_PRIVATE, null);
            //myDatabase.execSQL("DROP TABLE IF EXISTS stories");
            myDatabase.execSQL("CREATE TABLE IF NOT EXISTS stories (id INTEGER, url VARCHAR, title VARCHAR, UNIQUE(id))");

            Cursor c = myDatabase.rawQuery("SELECT * FROM stories ORDER BY id DESC LIMIT 5", null);
            int idIndex = c.getColumnIndex("id");
            int urlIndex = c.getColumnIndex("url");
            int titleIndex = c.getColumnIndex("title");
            while (c.moveToNext()) {
                String storyId = c.getString(idIndex);
                String storyTitle = c.getString(titleIndex);
                String storyUrl = c.getString(urlIndex);
                StoryData story = new StoryData(storyId, storyTitle, storyUrl);
                stories.add(story);
            }
            c.close();
            adapter.notifyDataSetChanged();
        }
        catch (Exception e){
            e.printStackTrace();
        }

        TaskGetNewStories getNewStories = new TaskGetNewStories();
        getNewStories.execute("https://hacker-news.firebaseio.com/v0/newstories.json");
    }

    class StoryData{
        String id;
        String title;
        String url;
        StoryData(String storyId){
            id = storyId;
        }
        StoryData(String storyID, String storyTitle, String storyUrl){
            id =storyID;
            title = storyTitle;
            url = storyUrl;
        }
        void setData(String storyURL, String storyTitle){
            url = storyURL;
            title = storyTitle;
        }

        boolean hasMissingData(){
            return (url == null || title == null);
        }

        @Override
        public String toString() {
            return title;
        }

        @Override
        public boolean equals(Object idData) {
            boolean isEqual = false;

            if(idData != null && idData instanceof StoryData){
                if(this.id.equals(((StoryData) idData).id)){
                    isEqual = true;
                }
            }
            return isEqual;
        }
    }

    class TaskGetNewStories extends AsyncTask<String, Void, String>{

        @Override
        protected String doInBackground(String... urls) {
            String resultString = "";

            try {
                URL url = new URL(urls[0]);
                URLConnection urlConnection = url.openConnection();
                InputStream inputStream = urlConnection.getInputStream();
                InputStreamReader reader = new InputStreamReader(inputStream);
                int data = reader.read();
                while(data != -1){
                    resultString += (char)data;
                    data = reader.read();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return resultString;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            try {
                JSONArray array = new JSONArray(s);
                for(int i = 0; i < array.length() ; i++){
                    String id = array.get(i).toString();
                    if(!stories.contains(new StoryData(id))) {
                        stories.add(new StoryData(id));
                    }
                }
                Log.i("done","done");
                adapter.notifyDataSetChanged();
                TaskGetStoryData getStoryData = new TaskGetStoryData();
                getStoryData.execute("https://hacker-news.firebaseio.com/v0/item/");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    class TaskGetStoryData extends AsyncTask<String, StoryData, String>{
        @Override
        protected String doInBackground(String... urls) {

            for(StoryData story : stories){
                if(!story.hasMissingData()){
                    continue;
                }
                String result = "";
                try {
                    URL url = new URL(String.format("%s%s.json?print=pretty" , urls[0], story.id));
                    URLConnection connection = url.openConnection();
                    InputStream inputStream = connection.getInputStream();
                    InputStreamReader reader = new InputStreamReader(inputStream);
                    for(int data = reader.read(); data != -1; data = reader.read()){
                        result += (char)data;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    JSONObject jsonObject = new JSONObject(result);
                    String urlData;
                    if(jsonObject.has("url")) {
                        urlData = jsonObject.getString("url");
                    }
                    else{
                        urlData = "https://news.ycombinator.com/item?id="+jsonObject.getString("id");
                    }
                    String titleData = jsonObject.getString("title");
                    story.setData(urlData, titleData);
                    publishProgress(story);
                } catch (JSONException e) {
                    Log.i("missing",result);
                    e.printStackTrace();
                }
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(StoryData... values) {
            super.onProgressUpdate(values);
            StoryData story = values[0];
            if(story.hasMissingData()){
                return;
            }
            ContentValues cv = new ContentValues();
            cv.put("id", story.id);
            cv.put("title", story.title);
            cv.put("url", story.url);
            myDatabase.insertWithOnConflict("stories", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            adapter.notifyDataSetChanged();
        }
    }
}
