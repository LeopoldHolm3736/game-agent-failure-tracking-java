package cc.infrai.game.client;

public final class InfraiApiException extends RuntimeException {
    private final String code;
    private final int statusCode;

    public InfraiApiException(String code, String message, int statusCode) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
    }

    public String code() { return code; }
    public int statusCode() { return statusCode; }
}
