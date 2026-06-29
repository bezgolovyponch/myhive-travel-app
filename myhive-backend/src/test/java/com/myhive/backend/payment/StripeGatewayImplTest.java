package com.myhive.backend.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myhive.backend.config.StripeProperties;
import com.myhive.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class StripeGatewayImplTest {

    @Test
    void constructEvent_throwsBadRequest_onInvalidSignature() {
        StripeProperties props = new StripeProperties("sk_test_dummy", "whsec_test_dummy", "eur", 30);
        StripeGatewayImpl gateway = new StripeGatewayImpl(props);

        assertThatThrownBy(() -> gateway.constructEvent("{\"id\":\"evt_1\"}", "t=1,v1=deadbeef"))
                .isInstanceOf(BadRequestException.class);
    }
}
