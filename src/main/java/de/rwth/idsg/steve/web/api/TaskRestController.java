/*
 * SteVe - SteckdosenVerwaltung - https://github.com/steve-community/steve
 * Copyright (C) 2013-2024 SteVe Community Team
 * All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.rwth.idsg.steve.web.api;

import de.rwth.idsg.steve.repository.TaskStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import de.rwth.idsg.steve.web.api.ApiControllerAdvice.ApiErrorResponse;
import de.rwth.idsg.steve.web.api.dto.ApiChargePointStart;
import de.rwth.idsg.steve.web.api.dto.ApiChargingProfile;
import de.rwth.idsg.steve.web.api.dto.ApiDataTransfer;
import de.rwth.idsg.steve.web.api.dto.ApiTaskInfo;
import de.rwth.idsg.steve.web.api.dto.ApiTaskList;
import de.rwth.idsg.steve.web.dto.ChargePointQueryForm;
import de.rwth.idsg.steve.web.dto.ocpp.DataTransferParams;
import de.rwth.idsg.steve.ocpp.task.DataTransferTask;
import de.rwth.idsg.steve.ocpp.ws.ocpp12.Ocpp12WebSocketEndpoint;
import de.rwth.idsg.steve.ocpp.ws.ocpp15.Ocpp15WebSocketEndpoint;
import de.rwth.idsg.steve.ocpp.ws.ocpp16.Ocpp16WebSocketEndpoint;
import de.rwth.idsg.steve.repository.ChargePointRepository;
import de.rwth.idsg.steve.repository.ChargingProfileRepository;

import de.rwth.idsg.steve.repository.dto.ChargePointSelect;
import de.rwth.idsg.steve.service.ChargePointService12_Client;
import de.rwth.idsg.steve.service.ChargePointService15_Client;
import de.rwth.idsg.steve.service.ChargePointService16_Client;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

import java.util.List;

/**
 * @author fnkbsi
 * @since 18.10.2023
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/v1/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class TaskRestController {

    @Autowired private TaskStore taskStore;
    @Autowired private ChargePointRepository chargePointRepository;
    /*
    @Autowired private ChargingProfileRepository repository;
    @Autowired private Ocpp12WebSocketEndpoint ocpp12WebSocketEndpoint;
    @Autowired private Ocpp15WebSocketEndpoint ocpp15WebSocketEndpoint;
    @Autowired private Ocpp16WebSocketEndpoint ocpp16WebSocketEndpoint;
    */
    @Autowired
    @Qualifier("ChargePointService12_Client")
    private ChargePointService12_Client client12;

    @Autowired
    @Qualifier("ChargePointService15_Client")
    private ChargePointService15_Client client15;

    @Autowired
    @Qualifier("ChargePointService16_Client")
    private ChargePointService16_Client client16;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ApiTaskList getTaskList() {
        ApiTaskList taskList = new ApiTaskList();
        taskList.setTasks(taskStore.getOverview());
        return taskList;
    }

    private String getOcppProtocol(String chargeBoxId) {
        ChargePointQueryForm form = new ChargePointQueryForm();
        form.setChargeBoxId(chargeBoxId);
        return chargePointRepository.getOverview(form).get(0).getOcppProtocol().toUpperCase();
    }

    // -------------------------------------------------------------------------
    // Http methods (GET)
    // -------------------------------------------------------------------------

    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 400, message = "Bad Request", response = ApiErrorResponse.class),
        @ApiResponse(code = 401, message = "Unauthorized", response = ApiErrorResponse.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ApiErrorResponse.class)}
    )
    @GetMapping(value = "taskoverview")
    @ResponseBody
    public ApiTaskList getOverview() {
        return getTaskList();
    }


    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 400, message = "Bad Request", response = ApiErrorResponse.class),
        @ApiResponse(code = 401, message = "Unauthorized", response = ApiErrorResponse.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ApiErrorResponse.class)}
    )
    @PostMapping(value = "clearfinishedtasks")
    @ResponseBody
    public ApiTaskList clearFinished() {
        taskStore.clearFinished();
        return getTaskList();
    }

    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 400, message = "Bad Request", response = ApiErrorResponse.class),
        @ApiResponse(code = 401, message = "Unauthorized", response = ApiErrorResponse.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ApiErrorResponse.class)}
    )
    @GetMapping(value = "task")
    @ResponseBody
    public ApiTaskInfo getTaskDetails(@Valid Integer taskId) {
        ApiTaskInfo taskInfo = new ApiTaskInfo(taskId, taskStore.get(taskId));
        return taskInfo;
    }

    // RS kamaste.it 08.2024
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK"),
        @ApiResponse(code = 400, message = "Bad Request", response = ApiErrorResponse.class),
        @ApiResponse(code = 401, message = "Unauthorized", response = ApiErrorResponse.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ApiErrorResponse.class)}
    )
    @PostMapping(value = "createdatatransfertask")
    @ResponseBody
    public Integer createDataTransferTask(@Valid ApiDataTransfer params) {
        log.info("Received createdatatransfertask POST request via API! ");
        
        List<ChargePointSelect> cps = chargePointRepository.getChargePointSelect(params.getChargeBoxId());
        
        for (ChargePointSelect chargepoint : cps) {
            String chargeBoxId = chargepoint.getChargeBoxId();
            String ocppProtocol = getOcppProtocol(chargeBoxId);
            DataTransferParams transactionParams = new DataTransferParams();
            transactionParams.setVendorId(params.getVendorId());
            transactionParams.setMessageId(params.getMessageId());
            transactionParams.setData(params.getData());
            transactionParams.setChargePointSelectList(cps);

            Integer taskId;
            taskId = switch (ocppProtocol) {
                case "OCPP1.6J", "OCPP1.6S" -> client16.dataTransfer(transactionParams);
                case "OCPP1.5J", "OCPP1.5S", "OCPP1.5" -> client15.dataTransfer(transactionParams);
                //case "OCPP1.2" -> client12.dataTransfer(transactionParams, "SteveWebApi");
                //default -> client12.dataTransfer(transactionParams, "SteveWebApi");
                case "OCPP1.2" -> -1;
                default -> -1;
            };
            if (taskId == -1) {
                log.error("OCPP 1.2 does not support Data Transfer. Cancelling...");
            }
            return taskId;

        }

        

        return 1;
    }
}
