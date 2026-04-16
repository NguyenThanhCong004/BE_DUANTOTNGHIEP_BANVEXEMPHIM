package com.fpoly.duan.controller;

import java.util.Comparator;
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
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.MovieDTO;
import com.fpoly.duan.dto.MovieWriteDTO;
import com.fpoly.duan.entity.Genre;
import com.fpoly.duan.entity.Movie;
import com.fpoly.duan.repository.GenreRepository;
import com.fpoly.duan.repository.MovieRepository;
import com.fpoly.duan.repository.ShowtimeRepository;
import com.fpoly.duan.repository.TicketRepository;

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

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final TicketRepository ticketRepository;
    private final ShowtimeRepository showtimeRepository;

    public MovieController(
            MovieRepository movieRepository,
            GenreRepository genreRepository,
            TicketRepository ticketRepository,
            ShowtimeRepository showtimeRepository) {
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
        this.ticketRepository = ticketRepository;
        this.showtimeRepository = showtimeRepository;
    }

    @GetMapping
    @Operation(summary = "Danh sách phim")
    public ResponseEntity<ApiResponse<List<MovieDTO>>> getAllMovies() {
        List<MovieDTO> movies = movieRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.<List<MovieDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách phim thành công")
                .data(movies)
                .build());
    }

    /**
     * Ảnh banner trang chủ: phim {@code status == 1} (đang chiếu), sắp xếp theo doanh thu vé → số suất chiếu.
     * Phim ngừng chiếu ({@code status != 1}) không đưa vào banner.
     */
    @GetMapping("/home-banners")
    @Operation(summary = "Banner trang chủ (theo doanh thu vé)")
    public ResponseEntity<ApiResponse<List<String>>> getHomeBanners() {
        List<Movie> active = movieRepository.findAll().stream()
                .filter(m -> m.getStatus() != null && m.getStatus() == 1)
                .collect(Collectors.toList());

        Map<Integer, Double> revenue = new HashMap<>();
        for (Object[] row : ticketRepository.sumTicketRevenueByMovieId()) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            Integer mid = (Integer) row[0];
            double rev = row[1] instanceof Number ? ((Number) row[1]).doubleValue() : 0.0;
            revenue.put(mid, rev);
        }

        Map<Integer, Long> showtimeCount = new HashMap<>();
        for (Object[] row : showtimeRepository.countShowtimesGroupedByMovieId()) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
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
                    if (b != null && !b.trim().isEmpty()) {
                        return b.trim();
                    }
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
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phim với id: " + id));
        return ResponseEntity.ok(ApiResponse.<MovieDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy thông tin phim thành công")
                .data(toDTO(m))
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo phim")
    @Transactional
    public ResponseEntity<ApiResponse<MovieDTO>> create(@RequestBody MovieWriteDTO req) {
        validateWriteCreate(req);
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
        log.debug("Update movie with id: {} and payload: {}", id, req);
        log.debug("Title: {}, GenreId: {}, Status: {}", req.getTitle(), req.getGenreId(), req.getStatus());
        Movie m = movieRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phim với id: " + id));

        // Kiểm tra xem có dữ liệu cập nhật không
        boolean hasChanges = false;

        // Kiểm tra từng field - chỉ coi là thay đổi nếu không null và không rỗng (đối với string)
        if (req.getGenreId() != null) hasChanges = true;
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

        log.debug("Has changes: {}", hasChanges);

        // Nếu không có thay đổi nào, trả về thông báo phù hợp
        if (!hasChanges) {
            log.debug("No changes detected for movie with id: {}", id);
            return ResponseEntity.ok(ApiResponse.<MovieDTO>builder()
                    .status(HttpStatus.OK.value())
                    .message("Không có thay đổi để cập nhật")
                    .data(toDTO(m))
                    .build());
        }

        validateWrite(req);
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
            throw new RuntimeException("Không tìm thấy phim với id: " + id);
        }
        movieRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa phim thành công")
                .build());
    }

    private void validateWrite(MovieWriteDTO req) {
        if (req == null) throw new RuntimeException("Dữ liệu phim không hợp lệ");
    }

    private void validateWriteCreate(MovieWriteDTO req) {
        validateWrite(req);
        if (req.getTitle() == null || req.getTitle().trim().isEmpty()) {
            throw new RuntimeException("Tên phim không được để trống");
        }
        if (req.getGenreId() == null) {
            throw new RuntimeException("Vui lòng chọn thể loại (genreId)");
        }
    }

    private Movie applyWrite(Movie m, MovieWriteDTO req) {
        // Chỉ cập nhật field khi giá trị được cung cấp (hỗ trợ partial update)
        if (req.getGenreId() != null) {
            Genre g = genreRepository.findById(req.getGenreId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thể loại với id: " + req.getGenreId()));
            m.setGenre(g);
        }
        if (req.getTitle() != null) {
            m.setTitle(req.getTitle().trim());
        }
        if (req.getDescription() != null) {
            m.setDescription(req.getDescription());
        }
        if (req.getDuration() != null) {
            m.setDuration(req.getDuration());
        }
        if (req.getAgeLimit() != null) {
            m.setAgeLimit(req.getAgeLimit());
        }
        if (req.getReleaseDate() != null) {
            m.setReleaseDate(req.getReleaseDate());
        }
        if (req.getPoster() != null) {
            m.setPoster(req.getPoster());
        }
        if (req.getStatus() != null) {
            m.setStatus(req.getStatus());
        }
        if (req.getBasePrice() != null) {
            m.setBasePrice(req.getBasePrice());
        }
        if (req.getAuthor() != null) {
            m.setAuthor(req.getAuthor());
        }
        if (req.getNation() != null) {
            m.setNation(req.getNation());
        }
        if (req.getContent() != null) {
            m.setContent(req.getContent());
        }
        if (req.getBanner() != null) {
            m.setBanner(req.getBanner());
        }
        return m;
    }

    private MovieDTO toDTO(Movie m) {
        Genre g = m.getGenre();
        return MovieDTO.builder()
                .id(m.getMovieId())
                .title(m.getTitle())
                .genre(g != null ? g.getName() : null)
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
                .build();
    }
}
