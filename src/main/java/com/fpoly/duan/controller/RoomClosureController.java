package com.fpoly.duan.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.AffectedOrderDTO;
import com.fpoly.duan.dto.AlternateShowtimeSuggestionDTO;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.AutoMoveResultDTO;
import com.fpoly.duan.dto.AutoMoveToSpareRoomRequest;
import com.fpoly.duan.dto.RoomCloseRequest;
import com.fpoly.duan.dto.RoomClosureMoveRequest;
import com.fpoly.duan.dto.RoomDTO;
import com.fpoly.duan.entity.Room;
import com.fpoly.duan.service.RoomClosureService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Đóng phòng chiếu tạm thời (hỏng ghế/sự cố kỹ thuật) và dời vé đã bán sang suất khác.
 * Tách riêng khỏi {@link RoomController} vì đây là 1 luồng nghiệp vụ (đóng → liệt kê đơn bị ảnh
 * hưởng → gợi ý suất thay thế → admin xác nhận dời/hủy từng đơn), không phải CRUD phòng thông thường.
 */
@RestController
@RequestMapping("/api/v1/rooms/{roomId}")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "8b. Đóng phòng & dời vé", description = "Đóng phòng tạm thời do sự cố, dời/hủy vé đã bán sang suất khác.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class RoomClosureController {

    private final RoomClosureService roomClosureService;

    @PostMapping("/close")
    @Operation(summary = "Đóng phòng tạm thời (kèm lý do)")
    public ResponseEntity<ApiResponse<RoomDTO>> closeRoom(@PathVariable Integer roomId,
            @Valid @RequestBody RoomCloseRequest request) {
        Room room = roomClosureService.closeRoom(roomId, request.getReason());
        return ResponseEntity.ok(ApiResponse.<RoomDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Đã đóng phòng tạm thời")
                .data(toDTO(room))
                .build());
    }

    @PostMapping("/reopen")
    @Operation(summary = "Mở lại phòng — trả kèm số đơn còn tồn đọng (chưa xử lý dời/hủy) nếu có")
    public ResponseEntity<ApiResponse<Integer>> reopenRoom(@PathVariable Integer roomId) {
        int remaining = roomClosureService.reopenRoom(roomId);
        return ResponseEntity.ok(ApiResponse.<Integer>builder()
                .status(HttpStatus.OK.value())
                .message(remaining > 0
                        ? "Đã mở lại phòng — còn " + remaining + " đơn chưa xử lý dời/hủy"
                        : "Đã mở lại phòng")
                .data(remaining)
                .build());
    }

    @PostMapping("/closure/auto-move-to-spare-type")
    @Operation(summary = "Dời hàng loạt suất chiếu (trong khoảng thời gian chọn) sang phòng trống cùng Loại phòng chiếu",
            description = "Admin chủ động bấm nút — không tự động khi đóng phòng. Suất nào không tìm được phòng phù hợp vẫn giữ nguyên, lộ ra ở /closure/affected-orders để xử lý thủ công.")
    public ResponseEntity<ApiResponse<AutoMoveResultDTO>> autoMoveToSpareRoomType(
            @PathVariable Integer roomId, @RequestBody AutoMoveToSpareRoomRequest request) {
        AutoMoveResultDTO result = roomClosureService.autoMoveMatchingRoomShowtimes(roomId,
                request != null ? request.getWindowEnd() : null);
        return ResponseEntity.ok(ApiResponse.<AutoMoveResultDTO>builder()
                .status(HttpStatus.OK.value())
                .message(result.getSummary())
                .data(result)
                .build());
    }

    @GetMapping("/closure/affected-orders")
    @Operation(summary = "Danh sách đơn hàng bị ảnh hưởng (vé ở suất Sắp chiếu trong phòng)")
    public ResponseEntity<ApiResponse<List<AffectedOrderDTO>>> affectedOrders(@PathVariable Integer roomId) {
        List<AffectedOrderDTO> orders = roomClosureService.listAffectedOrders(roomId);
        return ResponseEntity.ok(ApiResponse.<List<AffectedOrderDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách đơn bị ảnh hưởng thành công")
                .data(orders)
                .build());
    }

    @GetMapping("/closure/orders/{orderId}/suggestions")
    @Operation(summary = "Gợi ý suất chiếu thay thế cho 1 đơn (cùng phim/rạp, đúng loại ghế)")
    public ResponseEntity<ApiResponse<List<AlternateShowtimeSuggestionDTO>>> suggestions(
            @PathVariable Integer roomId, @PathVariable Integer orderId) {
        List<AlternateShowtimeSuggestionDTO> suggestions = roomClosureService.suggestAlternateShowtimes(roomId, orderId);
        return ResponseEntity.ok(ApiResponse.<List<AlternateShowtimeSuggestionDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy gợi ý suất chiếu thay thế thành công")
                .data(suggestions)
                .build());
    }

    @PostMapping("/closure/orders/{orderId}/move")
    @Operation(summary = "Xác nhận dời đơn sang suất chiếu thay thế")
    public ResponseEntity<ApiResponse<Void>> move(@PathVariable Integer roomId, @PathVariable Integer orderId,
            @Valid @RequestBody RoomClosureMoveRequest request) {
        roomClosureService.confirmMove(roomId, orderId, request.getTargetShowtimeId());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Đã dời vé sang suất chiếu mới và gửi email thông báo cho khách hàng")
                .build());
    }

    @PostMapping("/closure/orders/{orderId}/cancel")
    @Operation(summary = "Hủy đơn do không tìm được suất thay thế phù hợp (ghi chú hoàn tiền thủ công)")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Integer roomId, @PathVariable Integer orderId) {
        roomClosureService.cancelForClosure(roomId, orderId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Đã hủy đơn và gửi email thông báo cho khách hàng")
                .build());
    }

    private RoomDTO toDTO(Room r) {
        return RoomDTO.builder()
                .id(r.getRoomId())
                .name(r.getName())
                .status(r.getStatus())
                .cinemaId(r.getCinema() != null ? r.getCinema().getCinemaId() : null)
                .closeReason(r.getCloseReason())
                .closedAt(r.getClosedAt())
                .build();
    }
}
