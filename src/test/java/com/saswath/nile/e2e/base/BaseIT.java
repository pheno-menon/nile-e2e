package com.saswath.nile.e2e.base;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import org.awaitility.Awaitility;
import org.testng.annotations.BeforeSuite;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Base class for all E2E integration tests.
 *  - Loads e2e.properties once per suite
 *  - Configures RestAssured base URI and default request spec
 *  - Polls /api/products (public endpoint) until the Docker stack is healthy
 *    before any test class runs
 */
public class BaseIT {

    private static final Properties props = new Properties();
    protected static String baseUrl;

    @BeforeSuite(alwaysRun = true)
    public void globalSetup() {
        loadProperties();
        configureRestAssured();
        awaitStackHealthy();
    }

    private void loadProperties() {
        try (InputStream in = BaseIT.class
                .getClassLoader()
                .getResourceAsStream("e2e.properties")) {
            if (in == null) {
                throw new IllegalStateException("e2e.properties not found on classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load e2e.properties", e);
        }
        baseUrl = props.getProperty("base.url", "http://localhost:8080");
    }

    private void configureRestAssured() {
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setBaseUri(baseUrl)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .log(LogDetail.ALL)
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private void awaitStackHealthy() {
        int timeoutSeconds = Integer.parseInt(
                props.getProperty("health.timeout.seconds", "60"));

        System.out.println("[BaseIT] Waiting up to " + timeoutSeconds
                + "s for Nile stack at " + baseUrl + " ...");

        Awaitility.await()
                .atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(3, TimeUnit.SECONDS)
                .ignoreExceptions()
                .until(() -> {
                    int status = RestAssured
                            .given()
                            .spec(RestAssured.requestSpecification)
                            .when()
                            .get("/api/products")
                            .then()
                            .extract()
                            .statusCode();
                    return status == 200;
                });

        System.out.println("[BaseIT] Stack is healthy — starting tests.");
    }

    protected static String prop(String key) {
        String value = props.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing e2e property: " + key);
        }
        return value;
    }
}