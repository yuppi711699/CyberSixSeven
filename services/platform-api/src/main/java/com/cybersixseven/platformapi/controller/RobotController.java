package com.cybersixseven.platformapi.controller;


import com.cybersixseven.platformapi.dto.CreateRobotRequest;
import com.cybersixseven.platformapi.dto.RobotResponse;
import com.cybersixseven.platformapi.dto.UpdateRobotRequest;
import com.cybersixseven.platformapi.service.RobotService;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;



@RestController
@RequestMapping("/api/robots")
public class RobotController {

    private final RobotService robotService;

    public RobotController(RobotService robotService) {
        this.robotService = robotService;
    }

    @GetMapping
    public List<RobotResponse> listRobots() {
        return robotService.list();
    }

    @GetMapping("/{id}")
    public RobotResponse getRobot(@PathVariable UUID id) {
        return robotService.get(id);
    }

    @PostMapping("")
    @ResponseStatus (HttpStatus.CREATED)
    public RobotResponse createRobot(@PathVariable UUID id, @RequestBody CreateRobotRequest request){
        return robotService.create(request);
    }

    @PutMapping("/{id}")
    public RobotResponse updateRobot (@PathVariable UUID id, @RequestBody UpdateRobotRequest request){
        return robotService.update(id, request);
    }

    @DeleteMapping ("/{id}")
    @ResponseStatus (HttpStatus.NO_CONTENT)
    public void deleteRobot (@PathVariable UUID id){
        robotService.delete(id);
    }

}
