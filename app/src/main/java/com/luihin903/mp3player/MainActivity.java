package com.luihin903.mp3player;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_AUDIO = 100;

    private ExoPlayer player;
    private ListView listView;
    private PlayerView playerView;
//    Button prevButton, playButton, nextButton;

//    ArrayList<File> songs = new ArrayList<>();
    ArrayAdapter<String> adapter;

    private final List<MediaItem> mediaItems = new ArrayList<>();
    private final List<String> songTitles = new ArrayList<>();
//    MediaPlayer mediaPlayer;
//    int currentSongIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.listView);
        playerView = findViewById(R.id.playerView);
//        prevButton = findViewById(R.id.prevButton);
//        playButton = findViewById(R.id.playButton);
//        nextButton = findViewById(R.id.nextButton);
        Log.d("Debug", "onCreate");
        checkPermission();
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQUEST_AUDIO);
            }
            else {
                Log.d("Debug", "checkPermission");
                initPlayer();
            }
        }
        else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_AUDIO);
            } else {
                Log.d("Debug", "checkPermission");
                initPlayer();
            }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("Debug", "success");
            initPlayer();
        }
    }

    private void loadSongs() {
        Log.d("Debug", "loadSongs()");
        Uri collection;
        if (Build.VERSION.SDK_INT >= 29) {
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        }
        else {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

        try (Cursor cursor = getContentResolver().query(
                collection,
                projection,
                selection,
                null,
                MediaStore.Audio.Media.DISPLAY_NAME + " ASC"
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    MediaItem item = new MediaItem.Builder().setUri(contentUri).setMediaId(name).build();
                    mediaItems.add(item);
                    songTitles.add(name);
                    readMetadata(contentUri);
                }
            }
        }
    }


    private void initPlayer() {
        Log.d("Debug", "initPlayer()");
        loadSongs();
        Log.d("Debug", songTitles.toString());

        player = new ExoPlayer.Builder(this).build();
        player.setMediaItems(mediaItems);
        player.prepare();

        playerView.setPlayer(player);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, songTitles);

        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            player.seekTo(position, 0);
            player.play();
        });
    }

    private void readMetadata(Uri uri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                File tempFile = File.createTempFile("tmp_audio", ".mp3", getCacheDir());
                FileOutputStream out = new FileOutputStream(tempFile);
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.close();
                inputStream.close();

                AudioFile audioFile = AudioFileIO.read(tempFile);
                Tag tag = audioFile.getTag();

                List<String> artists = tag.getAll(FieldKey.ARTIST);
                String lyrics = tag.getFirst(FieldKey.LYRICS);
                List<TagField> paddings = tag.getFields("TXXX:PADDINGS");


                List<String> keys = new ArrayList<>();
                for (Iterator<TagField> it = tag.getFields(); it.hasNext(); ) {
                    TagField field = it.next();
                    keys.add(field.getId());
                    keys.add(field.toString());
                }
                Log.d("ID3", keys.toString());
                Log.d("ID3", artists.toString());
                Log.d("ID3", lyrics);
                Log.d("ID3", paddings.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}