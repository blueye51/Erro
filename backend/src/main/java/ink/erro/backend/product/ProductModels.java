package ink.erro.backend.product;

import java.math.BigDecimal;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ink.erro.backend.electrical.ElectricalValidationService.Check;

public final class ProductModels {
    private ProductModels() {}
    public record Rating(@NotNull @Positive BigDecimal voltage, @Pattern(regexp="AC|DC") @NotNull String currentType,
                         @Size(max=30) String utilizationCategory, @Positive BigDecimal ratedCurrent, @Positive BigDecimal motorPower,
                         @Positive BigDecimal icu, @Positive BigDecimal ics, @Positive BigDecimal icn,
                         @Size(max=100) String standardNumber, @Size(max=2000) String conditions) {}
    public record Standard(@NotBlank @Size(max=100) String standardNumber,@Size(max=100) String edition,@Size(max=300) String certification) {}
    public record ProductInput(@NotBlank @Size(max=200) String manufacturer,@Size(max=200) String series,
        @NotBlank @Size(max=200) String partNumber,@Size(max=30) String gtin,
        @NotBlank @Pattern(regexp="CIRCUIT_BREAKER|FUSE|RCD|RCBO|CONTACTOR|MOTOR|TRANSFORMER|POWER_SUPPLY|RELAY|PLC|ENCLOSURE|SURGE_PROTECTION|ACCESSORY") String productType,
        @NotBlank @Size(max=300) String name,@Size(max=2000) String description,
        @Positive BigDecimal ratedVoltageAc,@Positive BigDecimal ratedVoltageDc,@Positive BigDecimal ratedCurrent,
        @Positive BigDecimal frequencyMin,@Positive BigDecimal frequencyMax,@Min(1) @Max(3) Integer phaseCount,@Min(1) @Max(8) Integer numberOfPoles,
        @Size(max=20) String tripCurve,@Size(max=100) String tripUnit,@Positive BigDecimal residualCurrent,@Size(max=30) String rcdType,
        @Positive BigDecimal coilVoltage,@Pattern(regexp="AC|DC") String coilVoltageType,
        @Positive BigDecimal inputVoltageMin,@Positive BigDecimal inputVoltageMax,@Positive BigDecimal outputVoltage,@Positive BigDecimal outputCurrent,
        @Size(max=100) String coordinationType,@Positive BigDecimal conductorMin,@Positive BigDecimal conductorMax,
        @Size(max=100) String conductorMaterial,@Size(max=100) String terminalType,@Pattern(regexp="IP[0-6X][0-9X]") String ipRating,
        BigDecimal operatingTemperatureMin,BigDecimal operatingTemperatureMax,@Size(max=100) String mountingMethod,
        Boolean ce,Boolean rohs,@Size(max=2000) String datasheetUrl,@Size(max=2000) String manualUrl,@Size(max=2000) String manufacturerProductUrl,
        @NotNull UUID sourceDocumentId,@Size(max=30) Map<@Size(max=100) String,@Size(max=500) String> additionalAttributes,
        @NotNull @Size(max=30) List<@Valid Rating> ratings,@NotNull @Size(max=30) List<@Valid Standard> standards) {}
    public record CompatibilityInput(@NotNull UUID productId,@NotNull UUID relatedProductId,
        @NotBlank @Size(max=60) String compatibilityType,@NotBlank @Size(max=2000) String conditions,
        @NotNull UUID sourceDocumentId,boolean verified,@Size(max=2000) String notes) {}
    public record Candidate(UUID id,Map<String,Object> specifications,List<Map<String,Object>> ratings,
                            List<Map<String,Object>> standards,List<Map<String,Object>> compatibility,List<Check> checks) {}
}
