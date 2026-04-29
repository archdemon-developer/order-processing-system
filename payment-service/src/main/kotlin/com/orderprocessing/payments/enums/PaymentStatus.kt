package com.orderprocessing.payments.enums

enum class PaymentStatus {
    RETRYING,
    SUCCESS,
    FAILED,
    PENDING,
    ;

    val isTerminal: Boolean get() = this == SUCCESS || this == FAILED
}
