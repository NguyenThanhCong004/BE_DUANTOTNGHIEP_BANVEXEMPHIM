package com.fpoly.duan.controller;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.PromotionGroupRequest;
import com.fpoly.duan.dto.PromotionGroupResponse;
import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.entity.Movie;
import com.fpoly.duan.entity.Promotion;
import com.fpoly.duan.repository.CinemaRepository;
import com.fpoly.duan.repository.MovieRepository;
import com.fpoly.duan.repository.PromotionRepository;
import com.fpoly.duan.service.CinemaScopeService;
import com.fpoly.duan.util.SearchUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "9. Khuyến mãi (Promotions)", description = "Nhóm KM theo rạp + nhiều phim — FE Promotion Management.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class PromotionController {
    private final PromotionRepository promotionRepository;
    private final MovieRepository movieRepository;
    private final CinemaRepository cinemaRepository;
    private final CinemaScopeService cinemaScopeService;

    @GetMapping
    @Operation(summary = "Danh sách khuyến mãi (đã gom nhóm)")
    public ResponseEntity<ApiResponse<List<PromotionGroupResponse>>> getPromotions(
            @Parameter(description = "Lọc theo rạp") @RequestParam(required = false) Integer cinemaId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q) {
        String term = SearchUtils.pick(search, keyword, q);
        // Bắt buộc chọn rạp — không trả toàn bộ KM (super admin chọn rạp ở FE).
        List<Promotion> promotions = cinemaId == null ? java.util.Collections.emptyList()
                : promotionRepository.findByCinema_CinemaId(cinemaId);

        Map<PromotionGroupKey, List<Promotion>> grouped = promotions.stream()
                .collect(Collectors.groupingBy(this::groupKey));

        LocalDate today = LocalDate.now();

        List<PromotionGroupResponse> response = grouped.entrySet().stream()
                .map(entry -> toGroupResponse(entry.getKey(), entry.getValue(), today))
                .filter(p -> SearchUtils.matches(term,
                        p.getId(), p.getTitle(), p.getStatus(), p.getDiscount_percent(), p.getStartDate(),
                        p.getEndDate(), p.getCinemaId(), String.join(" ", p.getSelectedMovieTitles())))
                .sorted(Comparator.comparing(PromotionGroupResponse::getId, Comparator.nullsLast(Integer::compareTo)).reversed())
                .collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.<List<PromotionGroupResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách khuyến mãi thành công")
                .data(response)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết nhóm khuyến mãi")
    public ResponseEntity<ApiResponse<PromotionGroupResponse>> getPromotionGroupById(@PathVariable Integer id) {
        Promotion rep = promotionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khuyến mãi với id: " + id));

        PromotionGroupKey key = groupKey(rep);
        LocalDate today = LocalDate.now();

        List<Promotion> group = promotionRepository.findByCinema_CinemaId(key.cinemaId).stream()
                .filter(p -> Objects.equals(p.getPromotionName(), key.promotionName)
                        && Objects.equals(p.getDiscountPercent(), key.discountPercent)
                        && Objects.equals(p.getStartDate(), key.startDate)
                        && Objects.equals(p.getEndDate(), key.endDate))
                .collect(Collectors.toList());

        if (group.isEmpty()) {
            throw new RuntimeException("Không tìm thấy nhóm khuyến mãi cho id: " + id);
        }

        PromotionGroupResponse dto = toGroupResponse(key, group, today);

        return ResponseEntity.ok(ApiResponse.<PromotionGroupResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy chi tiết khuyến mãi thành công")
                .data(dto)
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo nhóm khuyến mãi (nhiều phim)")
    @Transactional
    public ResponseEntity<ApiResponse<Integer>> createPromotionGroup(@RequestBody PromotionGroupRequest request) {
        validateRequest(request);

        Integer cinemaId = request.getCinemaId();
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy rạp với mã: " + cinemaId));
        cinemaScopeService.requireCinemaAccess(cinema);
        cinemaScopeService.requireCinemaOperational(cinema);

        List<Integer> movieIds = request.getSelectedMovieIds();
        if (movieIds == null || movieIds.isEmpty()) {
            throw new RuntimeException("Vui lòng chọn ít nhất 1 phim");
        }
        ensureNoOverlappingPromotion(cinemaId, movieIds, request.getStart_date(), request.getEnd_date());

        LocalDate today = LocalDate.now();
        Integer statusInt = computeStatus(request.getStart_date(), request.getEnd_date(), today);

        // Tạo 1 bản ghi Promotion cho mỗi movieId (FE chọn nhiều phim)
        List<Promotion> created = movieIds.stream().map(movieId -> {
            Movie movie = movieRepository.findById(movieId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy phim với mã: " + movieId));
            Promotion p = new Promotion();
            p.setPromotionName(request.getTitle());
            p.setDiscountPercent(request.getDiscount_percent());
            p.setStartDate(request.getStart_date());
            p.setEndDate(request.getEnd_date());
            p.setStatus(statusInt);
            p.setMovie(movie);
            p.setCinema(cinema);
            return p;
        }).map(promotionRepository::save).collect(Collectors.toList());

        Integer repId = created.stream()
                .map(Promotion::getPromotionId)
                .min(Integer::compareTo)
                .orElseThrow();

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Integer>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo khuyến mãi thành công")
                .data(repId)
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật nhóm khuyến mãi")
    @Transactional
    public ResponseEntity<ApiResponse<Integer>> updatePromotionGroup(
            @PathVariable Integer id,
            @RequestBody PromotionGroupRequest request) {
        validateRequest(request);

        Promotion rep = promotionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khuyến mãi với id: " + id));

        PromotionGroupKey oldKey = groupKey(rep);
        cinemaScopeService.requireCinemaAccess(oldKey.getCinemaId());
        Cinema oldCinema = cinemaRepository.findById(oldKey.getCinemaId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy rạp với mã: " + oldKey.getCinemaId()));
        cinemaScopeService.requireCinemaOperational(oldCinema);

        // Xác định nhóm cũ (chưa xóa vội) để có thể loại trừ chính nó khi kiểm tra trùng lặp bên dưới.
        List<Promotion> candidates = promotionRepository.findByCinema_CinemaId(oldKey.getCinemaId());
        List<Promotion> oldGroup = candidates.stream()
                .filter(p -> Objects.equals(p.getPromotionName(), oldKey.getPromotionName())
                        && Objects.equals(p.getDiscountPercent(), oldKey.getDiscountPercent())
                        && Objects.equals(p.getStartDate(), oldKey.getStartDate())
                        && Objects.equals(p.getEndDate(), oldKey.getEndDate()))
                .collect(Collectors.toList());
        Set<Integer> oldGroupIds = oldGroup.stream()
                .map(Promotion::getPromotionId)
                .collect(Collectors.toSet());

        Integer cinemaId = request.getCinemaId() != null ? request.getCinemaId() : oldKey.getCinemaId();
        request.setCinemaId(cinemaId);

        List<Integer> movieIds = request.getSelectedMovieIds();
        if (movieIds == null || movieIds.isEmpty()) {
            throw new RuntimeException("Vui lòng chọn ít nhất 1 phim");
        }

        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy rạp với mã: " + cinemaId));
        cinemaScopeService.requireCinemaAccess(cinema);
        cinemaScopeService.requireCinemaOperational(cinema);

        // Kiểm tra hợp lệ TRƯỚC khi xóa nhóm cũ — tránh trường hợp báo lỗi nhưng khuyến mãi
        // cũ đã bị xóa mất (không rollback được vì đã flush). Loại trừ chính nhóm đang sửa.
        ensureNoOverlappingPromotion(cinemaId, movieIds, request.getStart_date(), request.getEnd_date(), oldGroupIds);

        // Mọi kiểm tra đã qua — giờ mới xóa nhóm cũ và tạo nhóm mới.
        promotionRepository.deleteAll(oldGroup);
        promotionRepository.flush(); // Cưỡng ép xóa ngay lập tức để tránh lỗi Unique

        LocalDate today = LocalDate.now();
        Integer statusInt = computeStatus(request.getStart_date(), request.getEnd_date(), today);

        List<Promotion> created = movieIds.stream().map(movieId -> {
            Movie movie = movieRepository.findById(movieId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy phim với mã: " + movieId));
            Promotion p = new Promotion();
            p.setPromotionName(request.getTitle());
            p.setDiscountPercent(request.getDiscount_percent());
            p.setStartDate(request.getStart_date());
            p.setEndDate(request.getEnd_date());
            p.setStatus(statusInt);
            p.setMovie(movie);
            p.setCinema(cinema);
            return p;
        }).map(promotionRepository::save).collect(Collectors.toList());

        Integer newRepId = created.stream()
                .map(Promotion::getPromotionId)
                .min(Integer::compareTo)
                .orElseThrow();

        return ResponseEntity.ok(ApiResponse.<Integer>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật khuyến mãi thành công")
                .data(newRepId)
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa nhóm khuyến mãi")
    public ResponseEntity<ApiResponse<Void>> deletePromotionGroup(@PathVariable Integer id) {
        Promotion rep = promotionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khuyến mãi với id: " + id));

        PromotionGroupKey key = groupKey(rep);
        cinemaScopeService.requireCinemaAccess(key.getCinemaId());
        Cinema cinema = cinemaRepository.findById(key.getCinemaId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy rạp với mã: " + key.getCinemaId()));
        cinemaScopeService.requireCinemaOperational(cinema);

        LocalDate today = LocalDate.now();
        if (computeStatus(rep.getStartDate(), rep.getEndDate(), today) == 1) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.<Void>builder()
                    .status(HttpStatus.BAD_REQUEST.value())
                    .message("Không được xóa khuyến mãi đang diễn ra.")
                    .build());
        }

        List<Promotion> group = promotionRepository.findByCinema_CinemaId(key.cinemaId).stream()
                .filter(p -> Objects.equals(p.getPromotionName(), key.promotionName)
                        && Objects.equals(p.getDiscountPercent(), key.discountPercent)
                        && Objects.equals(p.getStartDate(), key.startDate)
                        && Objects.equals(p.getEndDate(), key.endDate))
                .collect(Collectors.toList());

        promotionRepository.deleteAll(group);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa khuyến mãi thành công")
                .build());
    }

    private void validateRequest(PromotionGroupRequest request) {
        if (request == null) {
            throw new RuntimeException("Dữ liệu khuyến mãi không hợp lệ");
        }
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new RuntimeException("Tên khuyến mãi không được để trống");
        }
        if (request.getDiscount_percent() == null || request.getDiscount_percent() <= 0) {
            throw new RuntimeException("Phần trăm giảm giá không hợp lệ");
        }
        if (request.getStart_date() == null || request.getEnd_date() == null) {
            throw new RuntimeException("Vui lòng chọn ngày bắt đầu và ngày kết thúc");
        }
        if (request.getEnd_date().isBefore(request.getStart_date())) {
            throw new RuntimeException("Ngày kết thúc phải sau ngày bắt đầu");
        }
        if (request.getStart_date().isBefore(LocalDate.now())) {
            throw new RuntimeException("Ngày bắt đầu không được là ngày quá khứ");
        }
    }

    // Chặn tạo/sửa khuyến mãi trùng: cùng một phim không được thuộc 2 khuyến mãi
    // có khoảng thời gian chồng lấn tại cùng một rạp (bao gồm cả trường hợp trùng y hệt).
    private void ensureNoOverlappingPromotion(Integer cinemaId, List<Integer> movieIds, LocalDate start, LocalDate end) {
        ensureNoOverlappingPromotion(cinemaId, movieIds, start, end, Set.of());
    }

    private void ensureNoOverlappingPromotion(Integer cinemaId, List<Integer> movieIds, LocalDate start, LocalDate end,
            Set<Integer> excludePromotionIds) {
        List<Promotion> existing = promotionRepository.findByCinema_CinemaId(cinemaId);
        for (Promotion p : existing) {
            if (excludePromotionIds != null && excludePromotionIds.contains(p.getPromotionId())) {
                continue;
            }
            Movie movie = p.getMovie();
            if (movie == null || movie.getMovieId() == null || !movieIds.contains(movie.getMovieId())) {
                continue;
            }
            boolean overlap = !p.getEndDate().isBefore(start) && !p.getStartDate().isAfter(end);
            if (overlap) {
                throw new RuntimeException("Phim \"" + movie.getTitle() + "\" đã có khuyến mãi \""
                        + p.getPromotionName() + "\" trong khoảng " + p.getStartDate() + " đến " + p.getEndDate()
                        + ". Vui lòng chọn thời gian khác hoặc bỏ chọn phim này.");
            }
        }
    }

    private Integer computeStatus(LocalDate startDate, LocalDate endDate, LocalDate today) {
        if (today.isBefore(startDate)) {
            return 0; // sắp diễn ra
        }
        if (today.isAfter(endDate)) {
            return 2; // đã kết thúc
        }
        return 1; // đang diễn ra
    }

    private String toStatusLabel(Integer statusInt) {
        return switch (statusInt) {
            case 0 -> "Sắp diễn ra";
            case 1 -> "Đang diễn ra";
            case 2 -> "Đã kết thúc";
            default -> "Đang diễn ra";
        };
    }

    private PromotionGroupResponse toGroupResponse(PromotionGroupKey key, List<Promotion> group, LocalDate today) {
        Integer repId = group.stream()
                .map(Promotion::getPromotionId)
                .min(Integer::compareTo)
                .orElse(null);

        Integer statusInt = computeStatus(key.startDate, key.endDate, today);

        Set<Integer> movieIds = group.stream()
                .map(p -> p.getMovie() != null ? p.getMovie().getMovieId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<String> titles = group.stream()
                .map(p -> p.getMovie() != null ? p.getMovie().getTitle() : null)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        return PromotionGroupResponse.builder()
                .id(repId)
                .title(key.promotionName)
                .discount_percent(key.discountPercent)
                .startDate(key.startDate)
                .endDate(key.endDate)
                .status(toStatusLabel(statusInt))
                .cinemaId(key.cinemaId)
                .selectedMovieIds(movieIds.stream().sorted().collect(Collectors.toList()))
                .selectedMovieTitles(titles)
                .build();
    }

    private PromotionGroupKey groupKey(Promotion p) {
        Cinema c = p.getCinema();
        Integer cinemaId = c != null ? c.getCinemaId() : null;
        return PromotionGroupKey.builder()
                .promotionName(p.getPromotionName())
                .discountPercent(p.getDiscountPercent())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .cinemaId(cinemaId)
                .build();
    }

    // Simple value-object for grouping.
    @lombok.Builder
    @lombok.Value
    private static class PromotionGroupKey {
        private final String promotionName;
        private final Double discountPercent;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final Integer cinemaId;
    }
}

