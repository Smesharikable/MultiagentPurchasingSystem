package study.masystems.purchasingsystem.exceptions;

/**
 *
 */
public class WrongProposalException extends Exception {
    public WrongProposalException(String message) {
        super(message);
    }

    public WrongProposalException(String message, Throwable cause) {
        super(message, cause);
    }

    public WrongProposalException(Throwable cause) {
        super(cause);
    }

    protected WrongProposalException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
