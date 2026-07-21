package org.telegram.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

public class FileManagerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(org.telegram.messenger.R.layout.activity_file_manager);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open File Manager", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // TODO: implement filters and listing using MediaController/MediaDataController

        Button refresh = findViewById(R.id.fm_refresh_button);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    // placeholder: refresh media list
                    Toast.makeText(FileManagerActivity.this, LocaleController.getString(R.string.Refresh), Toast.LENGTH_SHORT).show();
                } catch (Exception ex) {
                    Toast.makeText(FileManagerActivity.this, "Refresh failed", Toast.LENGTH_SHORT).show();
                }
            }
        });

        Button goToMessage = findViewById(R.id.fm_gotomessage_button);
        goToMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    // launch placeholder activity to go to message
                    Toast.makeText(FileManagerActivity.this, "Go to message not implemented", Toast.LENGTH_SHORT).show();
                } catch (Exception ex) {
                    Toast.makeText(FileManagerActivity.this, "Action failed", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
