package com.zenbarrier.newsreader;

import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
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

        TaskGetNewStories getNewStories = new TaskGetNewStories();
        myDatabase = this.openOrCreateDatabase("hackerNews",MODE_PRIVATE, null);
        myDatabase.execSQL("CREATE TABLE IF NOT EXISTS stories (id INTEGER, url VARCHAR, title VARCHAR, UNIQUE(id))");
        getNewStories.execute("https://hacker-news.firebaseio.com/v0/newstories.json");
    }

    class StoryData{
        String id;
        String title;
        String url;
        StoryData(String storyId){
            id = storyId;
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
            //TODO change id to title
            return id;
        }

        @Override
        public boolean equals(Object idData) {
            boolean isEqual = false;

            if(idData != null && idData instanceof String){
                if(id == ((String)idData)){
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
                    String sql = String.format("INSERT OR IGNORE INTO stories (id) VALUES (%s)", array.get(i).toString());
                    myDatabase.execSQL(sql);
                    String id = array.get(i).toString();
                    if(!stories.contains(id)) {
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

    class TaskGetStoryData extends AsyncTask<String, Void, String>{
        @Override
        protected String doInBackground(String... urls) {

            for(StoryData story : stories){
                try {
                    URL url = new URL(String.format("%s%s.json" , urls[0], story.id));
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }
    }
}
