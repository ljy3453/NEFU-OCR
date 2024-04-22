package com.nefu.ocr;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.io.File;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        Preference deleteDataPreference = findPreference("pref_key_delete_data");
        deleteDataPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                deleteData();
                return true;
            }
        });
    }
    private void deleteData() {
        File externalFilesDir = requireContext().getExternalFilesDir(null);
        Log.d("Tag", String.valueOf(externalFilesDir));
        if (externalFilesDir != null && externalFilesDir.exists()) {
            if (deleteRecursive(externalFilesDir)) {
                Toast.makeText(requireContext(), "缓存清除成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "缓存清除失败", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(requireContext(), "暂无缓存", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        return fileOrDirectory.delete();
    }
}
