package com.saswath.nile.e2e.product;

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
 * E2E tests for /api/products and /api/admin/products.
 *  - Public GET access (no token required)
 *  - Admin CRUD (create, update, delete)
 *  - RBAC enforcement (regular user blocked from write operations)
 */
public class ProductIT extends BaseIT {

    private String userToken;
    private String adminToken;
    private long createdProductId;

    @BeforeClass
    public void setUpUsers() {
        String userEmail = "prod.user." + UUID.randomUUID() + "@nile.com";
        userToken  = AuthHelper.registerAndGetToken("Prod User", userEmail, "ValidPass1!");
        adminToken = AuthHelper.loginAndGetToken(prop("test.admin.email"), prop("test.admin.password"));
    }

    @Test(description = "GET /api/products returns 200 without any token")
    public void getAllProducts_public_returns200() {
        given()
                .spec(RestAssured.requestSpecification)
                .when()
                .get("/api/products")
                .then()
                .statusCode(200)
                .body("$", instanceOf(java.util.List.class));
    }

    @Test(description = "GET /api/products returns 200 with a valid user token")
    public void getAllProducts_withUserToken_returns200() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .when()
                .get("/api/products")
                .then()
                .statusCode(200);
    }

    @Test(
            description = "POST /api/admin/products creates product when called by admin",
            dependsOnMethods = "getAllProducts_public_returns200"
    )
    public void createProduct_asAdmin_returns200() {
        String productName = "E2E Widget " + UUID.randomUUID();

        Response response = given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", adminToken)
                .body(Map.of("name", productName, "price", 12.99, "stock", 100))
                .when()
                .post("/api/admin/products")
                .then()
                .statusCode(200)
                .body("name", equalTo(productName))
                .body("stockQuantity", equalTo(100))
                .extract().response();

        createdProductId = response.jsonPath().getLong("id");
    }

    @Test(description = "POST /api/admin/products returns 403 when called by regular user")
    public void createProduct_asUser_returns403() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .body(Map.of("name", "Forbidden Product", "price", 5.00, "stock", 10))
                .when()
                .post("/api/admin/products")
                .then()
                .statusCode(403);
    }

    @Test(description = "POST /api/admin/products returns 403 without token")
    public void createProduct_noToken_returns403() {
        given()
                .spec(RestAssured.requestSpecification)
                .body(Map.of("name", "No Auth Product", "price", 5.00, "stock", 10))
                .when()
                .post("/api/admin/products")
                .then()
                .statusCode(403);
    }

    @Test(
            description = "GET /api/products/{id} returns 200 for an existing product",
            dependsOnMethods = "createProduct_asAdmin_returns200"
    )
    public void getProductById_exists_returns200() {
        given()
                .spec(RestAssured.requestSpecification)
                .when()
                .get("/api/products/{id}", createdProductId)
                .then()
                .statusCode(200)
                .body("id", equalTo((int) createdProductId));
    }

    @Test(description = "GET /api/products/{id} returns 404 for a non-existent product")
    public void getProductById_notFound_returns404() {
        given()
                .spec(RestAssured.requestSpecification)
                .when()
                .get("/api/products/{id}", 999999L)
                .then()
                .statusCode(404);
    }

    @Test(
            description = "PUT /api/admin/products/{id} updates product fields when called by admin",
            dependsOnMethods = "createProduct_asAdmin_returns200"
    )
    public void updateProduct_asAdmin_returns200() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", adminToken)
                .body(Map.of("name", "Updated E2E Widget", "price", 19.99, "stock", 75))
                .when()
                .put("/api/admin/products/{id}", createdProductId)
                .then()
                .statusCode(200)
                .body("name", equalTo("Updated E2E Widget"))
                .body("price", equalTo(19.99f))
                .body("stockQuantity", equalTo(75));
    }

    @Test(
            description = "PUT /api/admin/products/{id} returns 403 when called by regular user",
            dependsOnMethods = "createProduct_asAdmin_returns200"
    )
    public void updateProduct_asUser_returns403() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .body(Map.of("name", "Hack", "price", 0.01, "stock", 9999))
                .when()
                .put("/api/admin/products/{id}", createdProductId)
                .then()
                .statusCode(403);
    }

    @Test(
            description = "DELETE /api/admin/products/{id} returns 403 when called by regular user",
            dependsOnMethods = "createProduct_asAdmin_returns200"
    )
    public void deleteProduct_asUser_returns403() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .when()
                .delete("/api/admin/products/{id}", createdProductId)
                .then()
                .statusCode(403);
    }

    @Test(
            description = "DELETE /api/admin/products/{id} returns 204 when called by admin",
            dependsOnMethods = {
                    "createProduct_asAdmin_returns200",
                    "getProductById_exists_returns200",
                    "updateProduct_asAdmin_returns200",
                    "deleteProduct_asUser_returns403"
            }
    )
    public void deleteProduct_asAdmin_returns204() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", adminToken)
                .when()
                .delete("/api/admin/products/{id}", createdProductId)
                .then()
                .statusCode(204);

        given()
                .spec(RestAssured.requestSpecification)
                .when()
                .get("/api/products/{id}", createdProductId)
                .then()
                .statusCode(404);
    }
}