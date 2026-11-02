package kr.pf.ballpark.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PingControllerTest {

    @Test
    void ping() {
        assertThat(new PingController().ping()).containsEntry("status", "ok");
    }
}
