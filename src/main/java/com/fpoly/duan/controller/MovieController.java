package com.fpoly.duan.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.MovieDTO;
import com.fpoly.duan.dto.MovieWriteDTO;
import com.fpoly.duan.dto.me.MeMovieReviewDto;
import com.fpoly.duan.entity.Genre;
import com.fpoly.duan.entity.Movie;
import com.fpoly.duan.entity.Showtime;
import com.fpoly.duan.repository.GenreRepository;
import com.fpoly.duan.repository.MovieRepository;
import com.fpoly.duan.repository.ReviewRepository;
import com.fpoly.duan.repository.ShowtimeRepository;
import com.fpoly.duan.repository.TicketRepository;
import com.fpoly.duan.service.CinemaScopeService;
import com.fpoly.duan.service.CustomerMeService;
import com.fpoly.duan.util.SearchUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/movies")
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "7. Phim (Movies)", description = "CRUD phim — FE quản trị.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class MovieController {

    private static final Logger log = LoggerFactory.getLogger(MovieController.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final TicketRepository ticketRepository;
    private final ShowtimeRepository showtimeRepository;
    private final ReviewRepository reviewRepository;
    private final CustomerMeService customerMeService;
    private final CinemaScopeService cinemaScopeService;

    public MovieController(
            MovieRepository movieRepository,
            GenreRepository genreRepository,
            TicketRepository ticketRepository,
            ShowtimeRepository showtimeRepository,
            ReviewRepository reviewRepository,
            CustomerMeService customerMeService,
            CinemaScopeService cinemaScopeService) {
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
        this.ticketRepository = ticketRepository;
        this.showtimeRepository = showtimeRepository;
        this.reviewRepository = reviewRepository;
        this.customerMeService = customerMeService;
        this.cinemaScopeService = cinemaScopeService;
    }

    @GetMapping
    @Operation(summary = "Danh sách phim")
    public ResponseEntity<ApiResponse<List<MovieDTO>>> getAllMovies(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q) {
        String term = SearchUtils.pick(search, keyword, q);
        Map<Integer, Double> ratingMap = reviewRepository.findAllAverageRatings().stream()
                .filter(row -> row != null && row.length >= 2 && row[0] != null)
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).intValue(),
                        row -> row[1] instanceof Number ? ((Number) row[1]).doubleValue() : null,
                        (a, b) -> a));
        List<MovieDTO> movies = movieRepository.findAll()
                .stream()
                .filter(m -> SearchUtils.matches(term,
                        m.getMovieId(), m.getTitle(), m.getAuthor(), m.getNation(), m.getDescription(),
                        m.getContent(), m.getStatus(), genreNames(m)))
                .map(m -> toDTO(m, ratingMap))
                .collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.<List<MovieDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách phim thành công")
                .data(movies)
                .build());
    }

    /**
     * Banner trang chủ: phim status==1 (đang chiếu), sắp xếp theo doanh thu vé → số suất chiếu.
     */
    @GetMapping("/home-banners")
    @Operation(summary = "Banner trang chủ (theo doanh thu vé)")
    public ResponseEntity<ApiResponse<List<String>>> getHomeBanners() {
        List<Movie> active = movieRepository.findAll().stream()
                .filter(m -> m.getStatus() != null && m.getStatus() == 1)
                .collect(Collectors.toList());

        Map<Integer, Double> revenue = new HashMap<>();
        for (Object[] row : ticketRepository.sumTicketRevenueByMovieId()) {
            if (row == null || row.length < 2 || row[0] == null) continue;
            Integer mid = (Integer) row[0];
            double rev = row[1] instanceof Number ? ((Number) row[1]).doubleValue() : 0.0;
            revenue.put(mid, rev);
        }

        Map<Integer, Long> showtimeCount = new HashMap<>();
        for (Object[] row : showtimeRepository.countShowtimesGroupedByMovieId()) {
            if (row == null || row.length < 2 || row[0] == null) continue;
            Integer mid = (Integer) row[0];
            long cnt = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
            showtimeCount.put(mid, cnt);
        }

        List<String> urls = active.stream()
                .sorted(Comparator
                        .comparing((Movie m) -> revenue.getOrDefault(m.getMovieId(), 0.0)).reversed()
                        .thenComparing(m -> showtimeCount.getOrDefault(m.getMovieId(), 0L), Comparator.reverseOrder())
                        .thenComparing(Movie::getMovieId, Comparator.reverseOrder()))
                .map(m -> {
                    String b = m.getBanner();
                    if (b != null && !b.trim().isEmpty()) return b.trim();
                    return m.getPoster() != null && !m.getPoster().trim().isEmpty() ? m.getPoster().trim() : null;
                })
                .filter(u -> u != null && !u.isEmpty())
                .limit(6)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.<List<String>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy banner trang chủ thành công")
                .data(urls)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phim")
    public ResponseEntity<ApiResponse<MovieDTO>> getById(@PathVariable Integer id) {
        Movie m = movieRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phim với id: " + id));
        return ResponseEntity.ok(ApiResponse.<MovieDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy thông tin phim thành công")
                .data(toDTO(m))
                .build());
    }

    @GetMapping("/{id}/reviews")
    @Operation(summary = "Danh sách bình luận / đánh giá của phim")
    public ResponseEntity<ApiResponse<List<MeMovieReviewDto>>> getMovieReviews(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.<List<MeMovieReviewDto>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy đánh giá phim thành công")
                .data(customerMeService.listMovieReviews(id))
                .build());
    }

    @GetMapping("/promotion-eligible")
    @Operation(summary = "Lọc phim phù hợp cho khuyến mãi", description = "Chỉ trả phim có suất chiếu tại rạp trong khoảng ngày đã chọn.")
    public ResponseEntity<ApiResponse<List<MovieDTO>>> getPromotionEligible(
            @org.springframework.web.bind.annotation.RequestParam Integer cinemaId,
            @org.springframework.web.bind.annotation.RequestParam String startDate,
            @org.springframework.web.bind.annotation.RequestParam String endDate) {
        cinemaScopeService.requireCinemaAccess(cinemaId);

        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(startDate);
            end = LocalDate.parse(endDate);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng ngày khuyến mãi không hợp lệ");
        }
        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày kết thúc không được trước ngày bắt đầu");
        }

        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();
        Map<Integer, Movie> uniqueMovies = new LinkedHashMap<>();
        List<Showtime> showtimes = showtimeRepository.findPromotionEligibleShowtimes(cinemaId, startAt, endExclusive);
        for (Showtime showtime : showtimes) {
            Movie movie = showtime.getMovie();
            if (movie == null || movie.getMovieId() == null) continue;
            Integer status = movie.getStatus();
            if (status != null && status == 2) continue;
            uniqueMovies.putIfAbsent(movie.getMovieId(), movie);
        }

        List<MovieDTO> movies = uniqueMovies.values().stream()
                .sorted(Comparator.comparing(Movie::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.<List<MovieDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(movies)
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo phim")
    @Transactional
    public ResponseEntity<ApiResponse<MovieDTO>> create(@Valid @RequestBody MovieWriteDTO req) {
        validateWriteCreate(req);
        validateMovieStatusDate(req.getStatus(), req.getReleaseDate());
        Movie m = applyWrite(new Movie(), req);
        Movie saved = movieRepository.save(m);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<MovieDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo phim thành công")
                .data(toDTO(saved))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật phim")
    @Transactional
    public ResponseEntity<ApiResponse<MovieDTO>> update(@PathVariable Integer id, @RequestBody MovieWriteDTO req) {
        log.debug("Update movie id={} payload={}", id, req);
        Movie m = movieRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phim với id: " + id));

        boolean hasChanges = false;
        if (req.getGenreIds() != null) hasChanges = true;
        if (req.getTitle() != null && !req.getTitle().trim().isEmpty()) hasChanges = true;
        if (req.getDescription() != null) hasChanges = true;
        if (req.getDuration() != null) hasChanges = true;
        if (req.getAgeLimit() != null) hasChanges = true;
        if (req.getReleaseDate() != null) hasChanges = true;
        if (req.getPoster() != null) hasChanges = true;
        if (req.getStatus() != null) hasChanges = true;
        if (req.getBasePrice() != null) hasChanges = true;
        if (req.getAuthor() != null) hasChanges = true;
        if (req.getNation() != null) hasChanges = true;
        if (req.getContent() != null) hasChanges = true;
        if (req.getBanner() != null) hasChanges = true;

        if (!hasChanges) {
            return ResponseEntity.ok(ApiResponse.<MovieDTO>builder()
                    .status(HttpStatus.OK.value())
                    .message("Không có thay đổi để cập nhật")
                    .data(toDTO(m))
                    .build());
        }

        if (req.getStatus() != null || req.getReleaseDate() != null) {
            Integer nextStatus = req.getStatus() != null ? req.getStatus() : m.getStatus();
            LocalDate nextReleaseDate = req.getReleaseDate() != null ? req.getReleaseDate() : m.getReleaseDate();
            validateMovieStatusDate(nextStatus, nextReleaseDate);
        }

        applyWrite(m, req);
        Movie saved = movieRepository.save(m);
        return ResponseEntity.ok(ApiResponse.<MovieDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật phim thành công")
                .data(toDTO(saved))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa phim")
    @Transactional
    // [SUPER ADMIN ONLY] - This section belongs to Super Admin. Do not modify without authorization.
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        if (!movieRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phim với id: " + id);
        }
        if (showtimeRepository.existsByMovie_MovieId(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Không thể xóa phim này vì đang có suất chiếu liên kết. Vui lòng xóa các suất chiếu trước.");
        }
        movieRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa phim thành công")
                .build());
    }

    private void validateWriteCreate(MovieWriteDTO req) {
        if (req.getTitle() == null || req.getTitle().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên phim không được để trống");
        }
        if (req.getGenreIds() == null || req.getGenreIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn thể loại phim");
        }
    }

    private void validateMovieStatusDate(Integer status, LocalDate releaseDate) {
        if (status == null || releaseDate == null) {
            return;
        }
        if (status < 0 || status > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái phim không hợp lệ");
        }
        if (status == 0) {
            return;
        }

        LocalDate today = LocalDate.now(APP_ZONE);
        if (status == 2 && !releaseDate.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trạng thái sắp chiếu chỉ dành cho phim có ngày khởi chiếu trong tương lai");
        }
        if (status == 1 && releaseDate.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Phim có ngày khởi chiếu trong tương lai phải để trạng thái sắp chiếu");
        }
    }

    private Movie applyWrite(Movie m, MovieWriteDTO req) {
        if (req.getGenreIds() != null) {
            List<Genre> genres = req.getGenreIds().stream()
                    .map(id -> genreRepository.findById(id)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thể loại với id: " + id)))
                    .collect(Collectors.toList());
            m.setGenres(genres);
        }
        if (req.getTitle() != null) m.setTitle(req.getTitle().trim());
        if (req.getDescription() != null) m.setDescription(req.getDescription());
        if (req.getDuration() != null) m.setDuration(req.getDuration());
        if (req.getAgeLimit() != null) m.setAgeLimit(req.getAgeLimit());
        if (req.getReleaseDate() != null) m.setReleaseDate(req.getReleaseDate());
        if (req.getPoster() != null) m.setPoster(req.getPoster());
        if (req.getStatus() != null) m.setStatus(req.getStatus());
        if (req.getBasePrice() != null) m.setBasePrice(req.getBasePrice());
        if (req.getAuthor() != null) m.setAuthor(req.getAuthor());
        if (req.getNation() != null) m.setNation(req.getNation());
        if (req.getContent() != null) m.setContent(req.getContent());
        if (req.getBanner() != null) m.setBanner(req.getBanner());
        return m;
    }

    private String genreNames(Movie m) {
        if (m.getGenres() == null || m.getGenres().isEmpty()) return null;
        return m.getGenres().stream().map(Genre::getName).collect(Collectors.joining(" "));
    }

    private MovieDTO toDTO(Movie m) {
        return buildMovieDTO(m, reviewRepository.findAverageRatingByMovieId(m.getMovieId()));
    }

    private MovieDTO toDTO(Movie m, Map<Integer, Double> ratingMap) {
        return buildMovieDTO(m, ratingMap.getOrDefault(m.getMovieId(), null));
    }

    private MovieDTO buildMovieDTO(Movie m, Double avgRating) {
        List<String> genreNames = m.getGenres() == null ? List.of()
                : m.getGenres().stream().map(Genre::getName).collect(Collectors.toList());
        return MovieDTO.builder()
                .id(m.getMovieId())
                .title(m.getTitle())
                .genres(genreNames)
                .posterUrl(m.getPoster())
                .duration(m.getDuration())
                .ageLimit(m.getAgeLimit())
                .releaseDate(m.getReleaseDate())
                .status(m.getStatus())
                .basePrice(m.getBasePrice())
                .author(m.getAuthor())
                .nation(m.getNation())
                .description(m.getDescription())
                .content(m.getContent())
                .banner(m.getBanner())
                .averageRating(avgRating)
                .build();
    }
}
