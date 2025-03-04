package com.banka1.banking.controllers;

import com.banka1.banking.dto.ExchangeMoneyTransferDTO;
import com.banka1.banking.dto.InternalTransferDTO;
import com.banka1.banking.services.ExchangeService;
import com.banka1.banking.services.TransferService;
import com.banka1.banking.utils.ResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/exchange-transfer")
@RequiredArgsConstructor
@Tag(name = "Menjačnica", description = "Ruta za upravljanje transferima sa konverzijom valute između računa istog korisnika")
public class ExchangeController {

    private final ExchangeService exchangeService;

    @Operation(
            summary = "Transfer sa konverzijom",
            description = "Izvršava transfer novca između različitih valuta za isti račun korisnika."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Interni prenos sa konverzijom uspešno izvršen",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{ \"success\": true, \"message\": \"Interni prenos sa konverzijom uspešno izvršen.\" }"))
            ),
            @ApiResponse(responseCode = "400", description = "Nevalidni podaci ili nedovoljno sredstava",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = "{ \"success\": false, \"error\": \"Nevalidni podaci ili nedovoljno sredstava.\" }"))
            )
    })
    @PostMapping
    public ResponseEntity<?> exchangeMoneyTransfer(
            @RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Podaci za transfer sa konverzijom",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ExchangeMoneyTransferDTO.class),
                            examples = @ExampleObject(value = "{ \"fromAccountId\": 1, \"toAccountId\": 2, \"amount\": 500.0, \"fromCurrency\": \"EUR\", \"toCurrency\": \"USD\" }"))
            ) ExchangeMoneyTransferDTO exchangeMoneyTransferDTO) {

        try {
            if(!exchangeService.validateExchangeTransfer(exchangeMoneyTransferDTO)){
                return ResponseTemplate.create(ResponseEntity.status(HttpStatus.BAD_REQUEST),
                        false,null,"Nevalidni podaci ili nedovoljno sredstava.");
            }

            exchangeService.createExchangeTransfer(exchangeMoneyTransferDTO);

            return ResponseTemplate.create(ResponseEntity.ok(),true, Map.of("message","Interni prenos sa konverzijom uspesno izvršen."),null);

        } catch (Exception e) {
            return ResponseTemplate.create(ResponseEntity.badRequest(), e);
        }

    }

}
