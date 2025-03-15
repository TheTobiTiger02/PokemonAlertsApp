package com.example.pokemonalerts;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PokemonViewModel extends ViewModel {
    private MutableLiveData<List<PokemonReport>> pokemonReports = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private MutableLiveData<String> error = new MutableLiveData<>();

    public LiveData<List<PokemonReport>> getPokemonReports() {
        return pokemonReports;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void loadPokemonReports() {
        isLoading.setValue(true);

        PokemonApiService apiService = ApiClient.getClient().create(PokemonApiService.class);
        Call<List<PokemonReport>> call = apiService.getPokemonReports();

        call.enqueue(new Callback<List<PokemonReport>>() {
            @Override
            public void onResponse(Call<List<PokemonReport>> call, Response<List<PokemonReport>> response) {
                isLoading.setValue(false);
                if (response.isSuccessful()) {
                    pokemonReports.setValue(response.body());
                } else {
                    error.setValue("Failed to fetch data: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<PokemonReport>> call, Throwable t) {
                isLoading.setValue(false);
                error.setValue("Network error: " + t.getMessage());
            }
        });
    }

    // Method to filter Pokemon reports by type
    public List<PokemonReport> filterByType(String type) {
        if (pokemonReports.getValue() == null) return new ArrayList<>();

        if (type == null || type.equals("All")) {
            return pokemonReports.getValue();
        }

        List<PokemonReport> filteredList = new ArrayList<>();
        for (PokemonReport report : pokemonReports.getValue()) {
            if (report.getType().equals(type)) {
                filteredList.add(report);
            }
        }
        return filteredList;
    }
}
