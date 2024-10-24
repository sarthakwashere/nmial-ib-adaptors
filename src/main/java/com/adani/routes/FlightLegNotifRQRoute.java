package com.adani.routes;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class FlightLegNotifRQRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        onException(Exception.class)
                .handled(true)
                .log("FlightLegNotifRQRouteException_001 :: Inside the FlightLegNotifRQRoute exception block========= ${exception.message}")
                .convertBodyTo(String.class)
                .setExchangePattern(ExchangePattern.InOnly)
                .choice()
                .when(header("subsystem").isEqualTo("BHS_IN_QUEUE"))
                .to("{{route.bhsOutQueue}}") // Send failure ack to BHS_OUT_QUEUE
                .log("FlightLegNotifRQRouteException_002 :: Failure acknowledgement pushed into BHS-OUT-QUEUE")
                .when(header("subsystem").isEqualTo("AODB_IN_QUEUE"))
                .to("{{route.aodbOutQueue}}") // Send failure ack to AODB_OUT_QUEUE
                .log("FlightLegNotifRQRouteException_003 :: Failure acknowledgement pushed into AODB-OUT-QUEUE")
                .otherwise()
                .to("{{route.dlq}}") // Send to Dead Letter Queue (DLQ)
                .log("FlightLegNotifRQRouteException_004 :: Request sent to DLQ due to unrecognized subsystem")
                .end()
                .to("velocity:{{route.FlightLegNotifRQ_Failure_Ack}}")
                .log("FlightLegNotifRQRouteException_005 :: Request is invalid : ${body}")
                .setHeader("Content-Type", simple("application/xml"))
        ;

        from("{{route.flightLegNotifRqQueue}}").routeId("FlightLegNotifRQRoute")
                .log("FlightLegNotifRQRoute_001 :: Hit received at ${date:now} and body >> ${body}")

                // Validate the request
                .to("validator:{{route.Xsd_FlightLegNotifRQ_validation}}")
                .log("FlightLegNotifRQRoute_002 :: Validation successful")

                // Conditional routing based on subsystem header
                .choice()
                .when(header("subsystem").isEqualTo("BHS_IN_QUEUE"))
                .log("FlightLegNotifRQRoute_003 :: Request received from BHS_IN_QUEUE")
                .to("amqp:queue:AODB-OUT-Queue") // Valid message goes to AODB_OUT_QUEUE
                .log("FlightLegNotifRQRoute_004 :: Message processed successfully into AODB-OUT-Queue")
                .when(header("subsystem").isEqualTo("AODB_IN_QUEUE"))
                .log("FlightLegNotifRQRoute_005 :: Request received from AODB_IN_QUEUE")
                .to("amqp:queue:BHS-OUT-Queue") // Valid message goes to BHS_OUT_QUEUE
                .log("FlightLegNotifRQRoute_006 :: Message processed successfully into BHS-OUT-Queue")
                .otherwise()
                .log("FlightLegNotifRQRoute_007 :: Unknown subsystem. Sending message to DLQ")
                .to("amqp:queue:DLQ") // Send to DLQ
                .end()
        ;
    }
}
