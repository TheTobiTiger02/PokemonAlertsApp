package com.example.pokemonalerts;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.HashSet;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Add the settings fragment to this Activity
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            // Set up auto-deselect behavior for both preferences.
            setupMultiSelectPreference("pref_display_types");
            setupMultiSelectPreference("pref_notify_types");
        }

        /**
         * Sets an OnPreferenceChangeListener on the MultiSelectListPreference identified by key.
         * The listener enforces the rule that:
         * • If "All" is selected along with any other item and "All" was just added,
         *   then everything else is cleared.
         * • Otherwise, if any item besides "All" is selected while "All" is already present,
         *   then "All" is removed.
         * • If the resulting set becomes empty, it defaults back to {"All"}.
         */
        private void setupMultiSelectPreference(String key) {
            MultiSelectListPreference pref = findPreference(key);
            if (pref != null) {
                pref.setOnPreferenceChangeListener((preference, newValue) -> {
                    // newValue is a Set<String>
                    Set<String> newSet = new HashSet<>((Set<String>) newValue);
                    Set<String> oldSet = new HashSet<>(pref.getValues());

                    if (newSet.contains("All") && newSet.size() > 1) {
                        // If "All" was just added (i.e. it was not in oldSet), then clear all
                        // and keep "All" only; otherwise, if "All" was already selected and now
                        // another item is selected, remove "All".
                        if (!oldSet.contains("All")) {
                            newSet.clear();
                            newSet.add("All");
                        } else {
                            newSet.remove("All");
                        }
                    }

                    // If nothing is selected, default back to "All"
                    if (newSet.isEmpty()) {
                        newSet.add("All");
                    }

                    pref.setValues(newSet);
                    // Return false because we update the value manually.
                    return false;
                });
            }
        }
    }
}
