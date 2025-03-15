package com.example.pokemonalerts;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;

public interface PokemonApiService {
    @GET("/api/pokemon")
    Call<List<PokemonReport>> getPokemonReports();

    @GET("/api/test")
    Call<Void> testApiEndpoint();
}
