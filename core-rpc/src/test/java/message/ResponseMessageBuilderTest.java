package message;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class ResponseMessageBuilderTest {

    @Test
    public void testFullConstructor() {
        // Given a result, error and id
        var rpcError = new RpcError(0, "message");
        var result = new Object();
        var builder = new ResponseMessage.Builder(1, rpcError, result);
        // When builder builds response
        var responseMessage = builder.build();
        // It should contain these objects and should have RESPONSE type
        assertEquals(1, responseMessage.getId());
        assertEquals(rpcError, responseMessage.getError());
        assertEquals(0, responseMessage.getError().getId());
        assertEquals("message", responseMessage.getError().getMessage());
        assertEquals(result, responseMessage.getResult());
        assertEquals(MessageType.RESPONSE, responseMessage.getType());
        // New calls should also work right
        assertEquals(1, builder.build().getId());
        assertEquals(rpcError, builder.build().getError());
        assertEquals(result, builder.build().getResult());
        assertEquals(MessageType.RESPONSE, builder.build().getType());
        // To string doesn't crash
        assertDoesNotThrow(() -> builder.build().toString());
    }

    @Test
    public void testNewInstanceEveryTime() {
        // Given a result, error and id
        var rpcError = new RpcError(0, "message");
        var result = new Object();
        var builder = new ResponseMessage.Builder(1, rpcError, result);
        // When builder builds multiple responses
        var responseMessage = builder.build();
        // They should not be same
        assertNotEquals(responseMessage, builder.build());
        // To string doesn't crash
        assertDoesNotThrow(() -> builder.build().toString());
    }

    @Test
    public void testResultConstructor() {
        // Given a result
        var result = new Object();
        var builder = new ResponseMessage.Builder(result);
        // When build is called
        // Result should contain given result
        assertEquals(result, builder.build().getResult());
        // And error should be null
        assertNull(builder.build().getError());
        // Type should still be RESPONSE
        assertEquals(MessageType.RESPONSE, builder.build().getType());
        // To string doesn't crash
        assertDoesNotThrow(() -> builder.build().toString());
    }

    @Test
    public void testErrorConstructor() {
        // Given an error
        var rpcError = new RpcError(0, "");
        var builder = new ResponseMessage.Builder(rpcError);
        // When build is called
        // It should contain given error
        assertEquals(rpcError, builder.build().getError());
        // And result should be null
        assertNull(builder.build().getResult());
        // Type should still be RESPONSE
        assertEquals(MessageType.RESPONSE, builder.build().getType());
        // To string doesn't crash
        assertDoesNotThrow(() -> builder.build().toString());
    }

    @Test
    public void testWithId() {
        // Given a builder with some defaults
        var builder = new ResponseMessage.Builder(0, null, null);
        assertEquals(0, builder.build().getId());
        // When withId is called, new id is used
        builder.withId(5);
        assertEquals(5, builder.build().getId());
        // Everything else is the same
        assertNull(builder.build().getResult());
        assertNull(builder.build().getError());
        assertEquals(MessageType.RESPONSE, builder.build().getType());
        // To string doesn't crash
        assertDoesNotThrow(() -> builder.build().toString());
    }

    @Test
    public void testWithError() {
        // Given a builder with some defaults
        var builder = new ResponseMessage.Builder(0, null, null);
        assertNull(builder.build().getError());
        var newError = new RpcError(0, "");
        // When withError is called, new error is used
        builder.withError(newError);
        assertEquals(newError, builder.build().getError());
        assertEquals(0, builder.build().getError().getId());
        assertEquals("", builder.build().getError().getMessage());
        // Everything else is the same
        assertNull(builder.build().getResult());
        assertEquals(0, builder.build().getId());
        assertEquals(MessageType.RESPONSE, builder.build().getType());
        // To string doesn't crash
        assertDoesNotThrow(() -> builder.build().toString());
    }

    @Test
    public void testWithResult() {
        // Given a builder with some defaults
        var builder = new ResponseMessage.Builder(0, null, null);
        assertNull(builder.build().getResult());
        var result = new Object();
        // When withError is called, new error is used
        builder.withResult(result);
        assertEquals(result, builder.build().getResult());
        // Everything else is the same
        assertNull(builder.build().getError());
        assertEquals(0, builder.build().getId());
        assertEquals(MessageType.RESPONSE, builder.build().getType());
        // To string doesn't crash
        assertDoesNotThrow(() -> builder.build().toString());
    }
}
