package org.folio.rest.impl;

import java.nio.file.NoSuchFileException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.folio.orders.rest.exceptions.HttpException;
import org.folio.orders.utils.HelperUtils;
import org.folio.rest.jaxrs.model.CompositePurchaseOrder;
import org.folio.rest.jaxrs.resource.OrdersResource.GetOrdersByIdResponse;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;

public class GetOrderHelper {

  private static final Logger logger = Logger.getLogger(GetOrderHelper.class);

  public static final String BASE_MOCK_DATA_PATH = "mockdata/";

  private final HttpClientInterface httpClient;
  private final Context ctx;
  private final Handler<AsyncResult<javax.ws.rs.core.Response>> asyncResultHandler;
  private final Map<String, String> okapiHeaders;

  public GetOrderHelper(HttpClientInterface httpClient, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context ctx) {
    this.httpClient = httpClient;
    this.okapiHeaders = okapiHeaders;
    this.ctx = ctx;
    this.asyncResultHandler = asyncResultHandler;
  }

  public CompletableFuture<CompositePurchaseOrder> getOrder(String id, String lang) {
    CompletableFuture<CompositePurchaseOrder> future = new VertxCompletableFuture<>(ctx);

    // TODO replace this with a call to mod-orders-storage
    getMockOrder(id)
      .thenAccept(orders -> {
        logger.info("Returning mock data: " + JsonObject.mapFrom(orders).encodePrettily());
        future.complete(orders);
      })
      .exceptionally(t -> {
        logger.error("Error getting orders", t);
        future.completeExceptionally(t);
        return null;
      });

    return future;
  }

  private CompletableFuture<CompositePurchaseOrder> getMockOrder(String id) {
    return VertxCompletableFuture.supplyAsync(ctx, () -> {
      try {
        JsonObject json = new JsonObject(HelperUtils.getMockData(String.format("%s%s.json", BASE_MOCK_DATA_PATH, id)));
        return json.mapTo(CompositePurchaseOrder.class);
      } catch (NoSuchFileException e) {
        logger.error("No such file", e);
        throw new CompletionException(new HttpException(404, id));
      } catch (Exception e) {
        logger.error("Failed to read mock data", e);
        throw new CompletionException(e);
      }
    });
  }

  public Void handleError(Throwable throwable) {
    final Future<javax.ws.rs.core.Response> result;

    logger.error("Exception querying for orders", throwable.getCause());

    final Throwable t = throwable.getCause();
    if (t instanceof HttpException) {
      final int code = ((HttpException) t).getCode();
      final String message = ((HttpException) t).getMessage();
      switch (code) {
      case 404:
        result = Future.succeededFuture(GetOrdersByIdResponse.withPlainNotFound(message));
        break;
      case 500:
        result = Future.succeededFuture(GetOrdersByIdResponse.withPlainInternalServerError(message));
        break;
      default:
        result = Future.succeededFuture(GetOrdersByIdResponse.withPlainInternalServerError(message));
      }
    } else {
      result = Future.succeededFuture(GetOrdersByIdResponse.withPlainInternalServerError(throwable.getMessage()));
    }

    httpClient.closeClient();

    asyncResultHandler.handle(result);

    return null;
  }

}
