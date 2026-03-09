package com.saswath.nile.e2e.admin;

import com.saswath.nile.e2e.base.AuthHelper;
import com.saswath.nile.e2e.base.BaseIT;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * E2E tests for /api/admin endpoints
 *  - GET /api/admin/users - lists all registered users
 *  - DELETE /api/admin/users/[id] - deletes user with specified ID
 *  - Product CRUD via /api/admin/products
 *  - Role based access control enforcement
 */
public class AdminIT extends BaseIT {
    private String adminToken;
    private String userToken;
    private Long targetUserId;
    private Long managedProductId;

    @BeforeClass
    public void setUpUsersAndProduct() {
        String userEmail = "admin.user." + UUID.randomUUID() + "@nile.com";
        String targetEmail = "admin.target." + UUID.randomUUID() + "@nile.com";

        adminToken = AuthHelper.loginAndGetToken(prop("test.admin.email"), prop("test.admin.password"));
        userToken = AuthHelper.registerAndGetToken("Regular User", userEmail, "ValidPass1!");

        // Throwaway user to be deleted
        Response targetResponse = AuthHelper.register("Target User", targetEmail, "ValidPass1!");
        targetResponse.then().statusCode(200);
        targetUserId = AuthHelper.extractUserId(targetResponse);

        // Create a product to use in admin product CRUD tests
        Response productResponse = given().spec(RestAssured.requestSpecification)
                .header("Authorization", adminToken)
                .body(Map.of("name", "Admin Managed Product " + UUID.randomUUID(),
                        "price", 25.00, "stock", 200))
                .when().post("/api/admin/products")
                .then().statusCode(200)
                .extract().response();
        managedProductId = productResponse.jsonPath().getLong("id");
    }

    @Test(description = "GET /api/admin/users returns 200 with list of users for admin")
    public void getAllUsers_asAdmin_returns200() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", adminToken)
                .when().get("/api/admin/users")
                .then().statusCode(200)
                .body("$", instanceOf(java.util.List.class))
                .body("$.size()", greaterThan(0))
                .body("[0].email", notNullValue())
                .body("[0].password", nullValue());
    }

    @Test(description = "GET /api/admin/users returns 403 for regular user")
    public void getAllUsers_asUser_returns403() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .when()
                .get("/api/admin/users")
                .then()
                .statusCode(403);
    }

    @Test(description = "GET /api/admin/users returns 403 without token")
    public void getAllUsers_noToken_returns403() {
        given()
                .spec(RestAssured.requestSpecification)
                .when()
                .get("/api/admin/users")
                .then()
                .statusCode(403);
    }

    @Test(description = "PUT /api/admin/products/{id} updates product for admin")
    public void updateProduct_asAdmin_returns200() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", adminToken)
                .body(Map.of("name", "Updated Admin Product", "price", 30.00, "stock", 150))
                .when()
                .put("/api/admin/products/{id}", managedProductId)
                .then()
                .statusCode(200)
                .body("name", equalTo("Updated Admin Product"))
                .body("stockQuantity", equalTo(150));
    }

    @Test(description = "PUT /api/admin/products/{id} returns 403 for regular user")
    public void updateProduct_asUser_returns403() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .body(Map.of("name", "Hack", "price", 0.01, "stock", 9999))
                .when()
                .put("/api/admin/products/{id}", managedProductId)
                .then()
                .statusCode(403);
    }

    @Test(
            description = "DELETE /api/admin/users/{id} returns 204 for admin",
            dependsOnMethods = "getAllUsers_asAdmin_returns200"
    )
    public void deleteUser_asAdmin_returns204() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", adminToken)
                .when()
                .delete("/api/admin/users/{id}", targetUserId)
                .then()
                .statusCode(204);
    }

    @Test(
            description = "DELETE /api/admin/users/{id} returns 404 for non-existent user"
    )
    public void deleteUser_notFound_returns404() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", adminToken)
                .when()
                .delete("/api/admin/users/{id}", 999999L)
                .then()
                .statusCode(404);
    }

    @Test(description = "DELETE /api/admin/users/{id} returns 403 for regular user")
    public void deleteUser_asUser_returns403() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .when()
                .delete("/api/admin/users/{id}", targetUserId)
                .then()
                .statusCode(403);
    }

    @Test(
            description = "DELETE /api/admin/products/{id} returns 204 for admin",
            dependsOnMethods = {"updateProduct_asAdmin_returns200", "updateProduct_asUser_returns403"}
    )
    public void deleteProduct_asAdmin_returns204() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", adminToken)
                .when()
                .delete("/api/admin/products/{id}", managedProductId)
                .then()
                .statusCode(204);
    }
}
