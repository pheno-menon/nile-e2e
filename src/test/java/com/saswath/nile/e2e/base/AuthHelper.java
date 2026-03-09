package com.saswath.nile.e2e.base;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.Map;

/**
 * Reusable authentication helper for E2E tests.
 * Provides static methods to register a new user, login an existing user,
 * and retrieve a ready-to-use Bearer token string.
 * All methods operate against the running Docker stack via RestAssured.
 */
public class AuthHelper {

    private AuthHelper() {}

    /**
     * Registers a new user and returns the full response.
     * Does NOT assert on status — callers check what they expect.
     */
    public static Response register(String name, String email, String password) {
        return RestAssured
                .given()
                .spec(RestAssured.requestSpecification)
                .body(Map.of("name", name, "email", email, "password", password))
                .when()
                .post("/api/auth/register");
    }

    /**
     * Logs in with the given credentials and returns the full response.
     * Does NOT assert on status — callers check what they expect.
     */
    public static Response login(String email, String password) {
        return RestAssured
                .given()
                .spec(RestAssured.requestSpecification)
                .body(Map.of("email", email, "password", password))
                .when()
                .post("/api/auth/login");
    }

    /**
     * Registers a user and returns a "Bearer <token>" string ready for
     * the Authorization header. Throws if registration fails.
     */
    public static String registerAndGetToken(String name, String email, String password) {
        Response response = register(name, email, password);
        response.then().statusCode(200);
        String token = response.jsonPath().getString("token");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "No token in register response for " + email);
        }
        return "Bearer " + token;
    }

    /**
     * Logs in and returns a "Bearer <token>" string ready for the
     * Authorization header. Throws if login fails.
     */
    public static String loginAndGetToken(String email, String password) {
        Response response = login(email, password);
        response.then().statusCode(200);
        String token = response.jsonPath().getString("token");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "No token in login response for " + email);
        }
        return "Bearer " + token;
    }

    /**
     * Extracts the user ID from a register or login response.
     */
    public static long extractUserId(Response response) {
        return response.jsonPath().getLong("user.id");
    }
}