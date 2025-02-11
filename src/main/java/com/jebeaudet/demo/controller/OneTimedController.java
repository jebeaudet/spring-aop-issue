package com.jebeaudet.demo.controller;

import io.micrometer.core.annotation.Timed;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Timed("test.one.timed")
@RestController
public class OneTimedController {

    @RequestMapping("/one")
    public String getOne() {
        return "One";
    }
}
