package com.saswath.nile.e2e.auth;

import com.saswath.nile.e2e.base.AuthHelper;
import com.saswath.nile.e2e.base.BaseIT;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * E2E tests for POST /api/auth/register and POST /api/auth/login.
 * Uses @DataProvider for invalid credential scenarios to avoid
 * duplicating nearly-identical test methods.
 */
public class AuthIT extends BaseIT {

    private final String uniqueEmail = "auth.test." + UUID.randomUUID() + "@nile.com";

    @BeforeClass
    public void registerBaseUser() {
        AuthHelper.register("Auth Test User", uniqueEmail, "ValidPass1!");
    }

    @Test(description = "Register with valid payload returns 200 with token and user")
    public void register_validPayload_returns200WithToken() {
        String email = "new." + UUID.randomUUID() + "@nile.com";

        given()
                .spec(RestAssured.requestSpecification)
                .body(Map.of("name", "New User", "email", email, "password", "ValidPass1!"))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(200)
                .body("token", not(emptyOrNullString()))
                .body("user.email", equalTo(email))
                .body("user.role", equalTo("ROLE_USER"))
                .body("user.password", nullValue());
    }

    @Test(description = "Register with duplicate email returns 400")
    public void register_duplicateEmail_returns400() {
        given()
                .spec(RestAssured.requestSpecification)
                .body(Map.of("name", "Duplicate", "email", uniqueEmail, "password", "AnyPass1!"))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400);
    }

    @Test(description = "Registered user can immediately log in with the same credentials")
    public void register_thenLogin_succeedsWithSameCredentials() {
        String email = "roundtrip." + UUID.randomUUID() + "@nile.com";
        String password = "RoundTrip1!";

        AuthHelper.register("Round Trip", email, password);

        given()
                .spec(RestAssured.requestSpecification)
                .body(Map.of("email", email, "password", password))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("token", not(emptyOrNullString()));
    }

    @Test(description = "Login with valid credentials returns 200 with token")
    public void login_validCredentials_returns200WithToken() {
        given()
                .spec(RestAssured.requestSpecification)
                .body(Map.of("email", uniqueEmail, "password", "ValidPass1!"))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("token", not(emptyOrNullString()))
                .body("user.email", equalTo(uniqueEmail))
                .body("user.password", nullValue());
    }

    @Test(description = "Login with wrong password returns 401")
    public void login_wrongPassword_returns401() {
        given()
                .spec(RestAssured.requestSpecification)
                .body(Map.of("email", uniqueEmail, "password", "WrongPassword!"))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401);
    }

    @Test(description = "Login with unregistered email returns 500")
    public void login_unknownEmail_returns500() {
        given()
                .spec(RestAssured.requestSpecification)
                .body(Map.of("email", "ghost@nile.com", "password", "AnyPass1!"))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(500);
    }

    /**
     * DataProvider supplies multiple invalid registration payloads.
     * Each row: { name, email, password, expectedStatus }
     * Tests that missing or incorrect fields are rejected.
     */
    @DataProvider(name = "invalidRegistrations")
    public Object[][] invalidRegistrations() {
        return new Object[][] {
                // missing name
                { "", "valid@nile.com", "ValidPass1!", 400 },
                // missing password
                { "User", "valid2@nile.com", "", 400 },
                // missing email
                { "User", "", "ValidPass1!", 400 },
        };
    }

    @Test(
            dataProvider = "invalidRegistrations",
            description = "Register with invalid payload returns 4xx"
    )
    public void register_invalidPayload_returns4xx(
            String name, String email, String password, int expectedStatus) {

        given()
                .spec(RestAssured.requestSpecification)
                .body(Map.of("name", name, "email", email, "password", password))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(expectedStatus);
    }
}