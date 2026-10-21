package jp.shuwa.restaurant.domain;

public final class Rejected extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public Rejected(String message) { super(message); }
}
