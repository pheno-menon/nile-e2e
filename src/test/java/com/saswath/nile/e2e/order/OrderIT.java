package com.saswath.nile.e2e.order;

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
 * E2E tests for /api/orders endpoints.
 * Tests the full order lifecycle:
 *   1. add to cart
 *   2. place order
 *   3. verify cart cleared
 *   4. verify order history
 * Uses dependsOnMethods to enforce execution order within the lifecycle chain.
 */
public class OrderIT extends BaseIT {

    private String userToken;
    private String adminToken;
    private long userId;
    private long productId;

    @BeforeClass
    public void setUpUsersAndProduct() {
        String userEmail  = "order.user."  + UUID.randomUUID() + "@nile.com";

        Response userResponse = AuthHelper.register("Order User", userEmail, "ValidPass1!");
        userResponse.then().statusCode(200);
        userToken = "Bearer " + userResponse.jsonPath().getString("token");
        userId    = AuthHelper.extractUserId(userResponse);

        adminToken = AuthHelper.loginAndGetToken(prop("test.admin.email"), prop("test.admin.password"));

        // Seed a product with enough stock
        Response productResponse = given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", adminToken)
                .body(Map.of("name", "Order Test Product " + UUID.randomUUID(),
                        "price", 15.00, "stock", 100))
                .when()
                .post("/api/admin/products")
                .then()
                .statusCode(200)
                .extract().response();

        productId = productResponse.jsonPath().getLong("id");
    }

    @Test(description = "POST /api/orders/place/{userId} returns 404 when cart is empty")
    public void placeOrder_emptyCart_returns404() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .when()
                .post("/api/orders/place/{userId}", userId)
                .then()
                .statusCode(404);
    }

    @Test(description = "POST /api/orders/place/{userId} returns 403 without token")
    public void placeOrder_noToken_returns403() {
        given()
                .spec(RestAssured.requestSpecification)
                .when()
                .post("/api/orders/place/{userId}", userId)
                .then()
                .statusCode(403);
    }

    @Test(
            description = "Add item to cart in preparation for order placement",
            dependsOnMethods = "placeOrder_emptyCart_returns404"
    )
    public void addItemToCartForOrder() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .body(Map.of("userId", userId, "productId", productId, "quantity", 3))
                .when()
                .post("/api/cart/add")
                .then()
                .statusCode(200);
    }

    @Test(
            description = "POST /api/orders/place/{userId} returns 200 with CREATED status and correct total",
            dependsOnMethods = "addItemToCartForOrder"
    )
    public void placeOrder_withCartItems_returns200() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .when()
                .post("/api/orders/place/{userId}", userId)
                .then()
                .statusCode(200)
                .body("status", equalTo("CREATED"))
                .body("totalAmount", equalTo(45.0f))    // 3 × 15.00
                .body("items", hasSize(1))
                .body("items[0].productName", notNullValue())
                .body("items[0].quantity", equalTo(3));
    }

    @Test(
            description = "Cart is empty immediately after order placement",
            dependsOnMethods = "placeOrder_withCartItems_returns200"
    )
    public void cart_isEmpty_afterOrderPlaced() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .when()
                .get("/api/cart/{userId}", userId)
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test(
            description = "Product stock is decremented after order placement",
            dependsOnMethods = "placeOrder_withCartItems_returns200"
    )
    public void productStock_decremented_afterOrderPlaced() {
        given()
                .spec(RestAssured.requestSpecification)
                .when()
                .get("/api/products/{id}", productId)
                .then()
                .statusCode(200)
                .body("stockQuantity", equalTo(97));   // 100 - 3 = 97
    }

    @Test(
            description = "GET /api/orders/user/{userId} returns the placed order in history",
            dependsOnMethods = "placeOrder_withCartItems_returns200"
    )
    public void getUserOrders_returnsPlacedOrder() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .when()
                .get("/api/orders/user/{userId}", userId)
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("[0].status", equalTo("CREATED"))
                .body("[0].items", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test(description = "GET /api/orders/user/{userId} returns 403 without token")
    public void getUserOrders_noToken_returns403() {
        given()
                .spec(RestAssured.requestSpecification)
                .when()
                .get("/api/orders/user/{userId}", userId)
                .then()
                .statusCode(403);
    }

    @Test(
            description = "GET /api/orders/user/{userId} returns 404 for a non-existent user"
    )
    public void getUserOrders_userNotFound_returns404() {
        given()
                .spec(RestAssured.requestSpecification)
                .header("Authorization", userToken)
                .when()
                .get("/api/orders/user/{userId}", 999999L)
                .then()
                .statusCode(404);
    }
}