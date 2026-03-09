package com.saswath.nile.e2e.cart;

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
 * E2E tests for /api/cart endpoints.
 * Uses dependsOnMethods to chain the cart lifecycle:
 *   1. add item
 *   2. get cart
 *   3. verify contents
 *   4. remove item
 *   5. verify empty
 * A product is seeded via the admin endpoint in @BeforeClass.
 */
public class CartIT extends BaseIT {

    private String userToken;
    private String adminToken;
    private long userId;
    private long productId;
    private long cartItemId;

    @BeforeClass
    public void setUpUsersAndProduct() {
        String userEmail  = "cart.user."  + UUID.randomUUID() + "@nile.com";
        String adminEmail = "cart.admin." + UUID.randomUUID() + "@nile.com";

        Response userResponse = AuthHelper.register("Cart User", userEmail, "ValidPass1!");
        userResponse.then().statusCode(200);
        userToken = "Bearer " + userResponse.jsonPath().getString("token");
        userId    = AuthHelper.extractUserId(userResponse);

        adminToken = AuthHelper.registerAndGetToken("Cart Admin", adminEmail, "ValidPass1!");

        // Seed a product via the admin endpoint for cart tests to use
        Response productResponse = given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", adminToken)
                .body(Map.of("name", "Cart Test Widget " + UUID.randomUUID(),
                        "price", 9.99, "stock", 50))
                .when()
                .post("/api/admin/products")
                .then()
                .statusCode(200)
                .extract().response();

        productId = productResponse.jsonPath().getLong("id");
    }

    @Test(description = "POST /api/cart/add returns 200 and CartItem with correct quantity")
    public void addToCart_validRequest_returns200() {
        Response response = given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .body(Map.of("userId", userId, "productId", productId, "quantity", 2))
                .when()
                .post("/api/cart/add")
                .then()
                .statusCode(200)
                .body("quantity", equalTo(2))
                .body("product.id", equalTo((int) productId))
                .extract().response();

        cartItemId = response.jsonPath().getLong("id");
    }

    @Test(description = "POST /api/cart/add returns 403 without token")
    public void addToCart_noToken_returns403() {
        given()
                .spec(RestAssured.requestSpecification)
                .body(Map.of("userId", userId, "productId", productId, "quantity", 1))
                .when()
                .post("/api/cart/add")
                .then()
                .statusCode(403);
    }

    @Test(description = "POST /api/cart/add returns 500 when quantity exceeds stock")
    public void addToCart_exceedsStock_returns500() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .body(Map.of("userId", userId, "productId", productId, "quantity", 99999))
                .when()
                .post("/api/cart/add")
                .then()
                .statusCode(500);
    }

    @Test(description = "POST /api/cart/add returns 404 when product does not exist")
    public void addToCart_productNotFound_returns404() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .body(Map.of("userId", userId, "productId", 999999L, "quantity", 1))
                .when()
                .post("/api/cart/add")
                .then()
                .statusCode(404);
    }

    @Test(
            description = "GET /api/cart/{userId} returns the item just added",
            dependsOnMethods = "addToCart_validRequest_returns200"
    )
    public void getCart_afterAdd_containsItem() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .when()
                .get("/api/cart/{userId}", userId)
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("[0].product.id", equalTo((int) productId))
                .body("[0].quantity", equalTo(2));
    }

    @Test(description = "GET /api/cart/{userId} returns 403 without token")
    public void getCart_noToken_returns403() {
        given()
                .spec(RestAssured.requestSpecification)
                .when()
                .get("/api/cart/{userId}", userId)
                .then()
                .statusCode(403);
    }

    @Test(
            description = "DELETE /api/cart/{cartItemId} removes item and cart becomes empty",
            dependsOnMethods = "getCart_afterAdd_containsItem"
    )
    public void removeFromCart_removesItem_cartBecomesEmpty() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .when()
                .delete("/api/cart/{cartItemId}", cartItemId)
                .then()
                .statusCode(204);

        // Verify cart is now empty
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .when()
                .get("/api/cart/{userId}", userId)
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }
}