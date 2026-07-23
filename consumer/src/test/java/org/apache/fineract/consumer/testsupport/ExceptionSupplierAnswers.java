package org.apache.fineract.consumer.testsupport;

import java.util.function.Supplier;
import org.apache.fineract.consumer.infrastructure.exception.AbstractConsumerException;
import org.mockito.stubbing.Answer;

public final class ExceptionSupplierAnswers {

    private ExceptionSupplierAnswers() {}

    public static Answer<Void> throwsCallerSuppliedException(int supplierArgIndex) {
        return invocation -> {
            Supplier<? extends AbstractConsumerException> supplier = invocation.getArgument(supplierArgIndex);
            throw supplier.get();
        };
    }
}
