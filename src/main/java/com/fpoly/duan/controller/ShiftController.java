package com.fpoly.duan.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.ShiftGroupRequest;
import com.fpoly.duan.dto.ShiftGroupResponse;
import com.fpoly.duan.dto.ShiftItemResponse;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.entity.StaffShift;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.repository.StaffRepository;
import com.fpoly.duan.repository.StaffShiftRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/shifts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "4. Ca làm (Shifts)", description = """
        Quản lý ca nhân viên theo rạp. FE: `AdminShiftForm.jsx` — GET/POST/PUT `/api/v1/shifts`.
        Query `cinemaId` lọc theo rạp.
        """)
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class ShiftController {
    private final StaffShiftRepository staffShiftRepository;
    private final StaffRepository staffRepository;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @GetMapping
    @Operation(summary = "Danh sách ca (theo rạp nếu có cinemaId)", description = "startDate/endDate (yyyy-MM-dd): lọc theo ngày ca — khớp lịch tuần FE.")
    public ResponseEntity<ApiResponse<List<ShiftItemResponse>>> getShifts(
            @Parameter(description = "Lọc theo rạp — khớp FE Admin khi chọn cinema")
            @RequestParam(required = false) Integer cinemaId,
            @Parameter(description = "Từ ngày (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Đến ngày (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<StaffShift> all = staffShiftRepository.findAll();

        List<StaffShift> filtered = all;
        if (cinemaId != null) {
            filtered = all.stream()
                    .filter(s -> {
                        Staff st = s.getStaff();
                        if (st == null) return false;
                        // staff.cinema có thể null => vẫn tính vào ca của rạp đang chọn
                        if (st.getCinema() == null) return true;
                        return cinemaId.equals(st.getCinema().getCinemaId());
                    })
                    .collect(Collectors.toList());
        }

        if (startDate != null) {
            filtered = filtered.stream()
                    .filter(s -> s.getDate() != null && !s.getDate().isBefore(startDate))
                    .collect(Collectors.toList());
        }
        if (endDate != null) {
            filtered = filtered.stream()
                    .filter(s -> s.getDate() != null && !s.getDate().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        LocalDateTime now = LocalDateTime.now();

        List<ShiftItemResponse> data = filtered.stream()
                .map(s -> toShiftItemResponse(s, now))
                .collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.<List<ShiftItemResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách ca làm thành công")
                .data(data)
                .build());
    }

    @GetMapping("/me")
    @Operation(summary = "Ca làm của tôi", description = "Nhân viên đang đăng nhập — chỉ trả các ca gán đúng staffId. Khách hàng (USER) không dùng được.")
    public ResponseEntity<ApiResponse<List<ShiftItemResponse>>> getMyShifts() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.<List<ShiftItemResponse>>builder()
                    .status(HttpStatus.UNAUTHORIZED.value())
                    .message("Chưa đăng nhập hoặc phiên không hợp lệ")
                    .data(List.of())
                    .build());
        }
        if (details.getStaff() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.<List<ShiftItemResponse>>builder()
                    .status(HttpStatus.FORBIDDEN.value())
                    .message("Chỉ tài khoản nhân viên mới xem được lịch ca của mình")
                    .data(List.of())
                    .build());
        }
        Integer staffId = details.getStaff().getStaffId();
        List<StaffShift> mine = staffShiftRepository.findByStaffStaffIdOrderByDateDescStartTimeAsc(staffId);
        LocalDateTime now = LocalDateTime.now();
        List<ShiftItemResponse> data = mine.stream()
                .map(s -> toShiftItemResponse(s, now))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.<List<ShiftItemResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy ca làm của bạn thành công")
                .data(data)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết một nhóm ca")
    public ResponseEntity<ApiResponse<ShiftGroupResponse>> getShiftGroup(@PathVariable Integer id) {
        StaffShift rep = staffShiftRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ca làm với id: " + id));

        LocalDate date = rep.getDate();
        LocalDateTime start = rep.getStartTime();
        LocalDateTime end = rep.getEndTime();

        String shiftType = deriveShiftType(start, end);

        List<StaffShift> group = staffShiftRepository.findByDateAndStartTimeAndEndTime(date, start, end);

        Integer banveId = null;
        Integer soatveId = null;
        Integer phucvuId = null;

        for (StaffShift ss : group) {
            Staff st = ss.getStaff();
            if (st == null) continue;
            String role = st.getRole();
            if ("Bán vé".equals(role)) banveId = st.getStaffId();
            else if ("Soát vé".equals(role)) soatveId = st.getStaffId();
            else if ("Phục vụ".equals(role)) phucvuId = st.getStaffId();
        }

        ShiftGroupResponse resp = ShiftGroupResponse.builder()
                .shiftType(shiftType)
                .date(date)
                .staffBanveId(banveId)
                .staffSoatVeId(soatveId)
                .staffPhucVuId(phucvuId)
                .build();

        return ResponseEntity.ok(ApiResponse.<ShiftGroupResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy chi tiết ca làm thành công")
                .data(resp)
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo nhóm ca")
    @Transactional
    public ResponseEntity<ApiResponse<Integer>> createShiftGroup(@RequestBody ShiftGroupRequest request) {
        validateRequest(request);

        LocalDate date = request.getDate();
        LocalDateTime[] range = resolveTimeRange(request.getShiftType(), date);

        Staff stBanve = staffRepository.findById(request.getStaffBanveId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy staff Bán vé"));
        Staff stSoatVe = staffRepository.findById(request.getStaffSoatVeId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy staff Soát vé"));
        Staff stPhucVu = staffRepository.findById(request.getStaffPhucVuId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy staff Phục vụ"));

        StaffShift s1 = buildShift(request.getShiftType(), date, range[0], range[1], stBanve);
        StaffShift s2 = buildShift(request.getShiftType(), date, range[0], range[1], stSoatVe);
        StaffShift s3 = buildShift(request.getShiftType(), date, range[0], range[1], stPhucVu);

        StaffShift created1 = staffShiftRepository.save(s1);
        staffShiftRepository.save(s2);
        staffShiftRepository.save(s3);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Integer>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo ca làm thành công")
                .data(created1.getStaffShiftId())
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật nhóm ca")
    @Transactional
    public ResponseEntity<ApiResponse<Integer>> updateShiftGroup(@PathVariable Integer id, @RequestBody ShiftGroupRequest request) {
        validateRequest(request);

        StaffShift rep = staffShiftRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ca làm với id: " + id));

        LocalDate date = request.getDate() != null ? request.getDate() : rep.getDate();
        LocalDateTime[] range = resolveTimeRange(request.getShiftType(), date);

        // Delete group and recreate (simple & consistent)
        List<StaffShift> toDelete = staffShiftRepository.findByDateAndStartTimeAndEndTime(rep.getDate(), rep.getStartTime(), rep.getEndTime());
        staffShiftRepository.deleteAll(toDelete);

        Staff stBanve = staffRepository.findById(request.getStaffBanveId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy staff Bán vé"));
        Staff stSoatVe = staffRepository.findById(request.getStaffSoatVeId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy staff Soát vé"));
        Staff stPhucVu = staffRepository.findById(request.getStaffPhucVuId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy staff Phục vụ"));

        StaffShift s1 = buildShift(request.getShiftType(), date, range[0], range[1], stBanve);
        StaffShift s2 = buildShift(request.getShiftType(), date, range[0], range[1], stSoatVe);
        StaffShift s3 = buildShift(request.getShiftType(), date, range[0], range[1], stPhucVu);

        StaffShift created1 = staffShiftRepository.save(s1);
        staffShiftRepository.save(s2);
        staffShiftRepository.save(s3);

        return ResponseEntity.ok(ApiResponse.<Integer>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật ca làm thành công")
                .data(created1.getStaffShiftId())
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa nhóm ca (cùng ngày, cùng khung giờ)")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteShiftGroup(@PathVariable Integer id) {
        StaffShift rep = staffShiftRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ca làm với id: " + id));
        List<StaffShift> toDelete = staffShiftRepository.findByDateAndStartTimeAndEndTime(
                rep.getDate(), rep.getStartTime(), rep.getEndTime());
        staffShiftRepository.deleteAll(toDelete);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa ca làm thành công")
                .build());
    }

    private void validateRequest(ShiftGroupRequest request) {
        if (request == null) throw new RuntimeException("Dữ liệu ca làm không hợp lệ");
        if (request.getDate() == null) throw new RuntimeException("Vui lòng chọn ngày ca làm");
        if (request.getShiftType() == null || request.getShiftType().trim().isEmpty()) throw new RuntimeException("Vui lòng chọn loại ca");
        if (request.getStaffBanveId() == null) throw new RuntimeException("Thiếu staff Bán vé");
        if (request.getStaffSoatVeId() == null) throw new RuntimeException("Thiếu staff Soát vé");
        if (request.getStaffPhucVuId() == null) throw new RuntimeException("Thiếu staff Phục vụ");
    }

    private StaffShift buildShift(String shiftType, LocalDate date, LocalDateTime start, LocalDateTime end, Staff staff) {
        StaffShift s = new StaffShift();
        s.setDate(date);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setStaff(staff);
        return s;
    }

    private LocalDateTime[] resolveTimeRange(String shiftType, LocalDate date) {
        LocalDateTime start;
        LocalDateTime end;
        switch (shiftType) {
            case "Ca 1":
                start = date.atTime(8, 0);
                end = date.atTime(13, 0);
                break;
            case "Ca 2":
                start = date.atTime(13, 0);
                end = date.atTime(18, 0);
                break;
            case "Ca 3":
                start = date.atTime(18, 0);
                end = date.atTime(23, 0);
                break;
            default:
                throw new RuntimeException("shiftType không hợp lệ: " + shiftType);
        }
        return new LocalDateTime[] { start, end };
    }

    private String deriveShiftType(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return "Ca 1";
        String s = start.toLocalTime().format(TIME_FMT);
        String e = end.toLocalTime().format(TIME_FMT);
        if ("08:00".equals(s) && "13:00".equals(e)) return "Ca 1";
        if ("13:00".equals(s) && "18:00".equals(e)) return "Ca 2";
        if ("18:00".equals(s) && "23:00".equals(e)) return "Ca 3";
        return "Ca 1";
    }

    private String statusLabel(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (start.isAfter(now)) return "Sắp tới";
        if (end.isBefore(now)) return "Đã xong";
        return "Đang làm";
    }

    private ShiftItemResponse toShiftItemResponse(StaffShift ss, LocalDateTime now) {
        Staff st = ss.getStaff();
        String role = st != null ? st.getRole() : null;
        String roleLabel;
        if ("Bán vé".equals(role)) roleLabel = "Bán vé";
        else if ("Soát vé".equals(role)) roleLabel = "Soát vé";
        else if ("Phục vụ".equals(role)) roleLabel = "Phục vụ";
        else roleLabel = role != null ? role : "";

        String shiftType = deriveShiftType(ss.getStartTime(), ss.getEndTime());

        return ShiftItemResponse.builder()
                .id(ss.getStaffShiftId())
                .staffName(st != null ? st.getFullname() : "")
                .role(roleLabel)
                .phone(st != null ? st.getPhone() : "")
                .date(ss.getDate() != null ? ss.getDate().toString() : null)
                .shiftType(shiftType)
                .startTime(ss.getStartTime() != null ? ss.getStartTime().toLocalTime().format(TIME_FMT) : null)
                .endTime(ss.getEndTime() != null ? ss.getEndTime().toLocalTime().format(TIME_FMT) : null)
                .status(statusLabel(ss.getStartTime(), ss.getEndTime(), now))
                .build();
    }
}

