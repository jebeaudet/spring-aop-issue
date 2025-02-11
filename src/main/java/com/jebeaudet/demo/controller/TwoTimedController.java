package com.jebeaudet.demo.controller;

import io.micrometer.core.annotation.Timed;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Timed("test.two.timed.first")
@Timed(value = "test.two.timed.second", histogram = true)
@RestController
public class TwoTimedController {

    @RequestMapping("/two")
    public String getTwo() {
        return "Two";
    }
}
