package com.fpoly.duan.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

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
import com.fpoly.duan.dto.ShowtimeRequest;
import com.fpoly.duan.dto.ShowtimeSlotResponse;
import com.fpoly.duan.entity.Movie;
import com.fpoly.duan.entity.Room;
import com.fpoly.duan.entity.Showtime;
import com.fpoly.duan.repository.MovieRepository;
import com.fpoly.duan.repository.RoomRepository;
import com.fpoly.duan.repository.ShowtimeRepository;
import com.fpoly.duan.repository.TicketRepository;
import com.fpoly.duan.service.CinemaScopeService;
import com.fpoly.duan.util.SearchUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/showtimes")
@Transactional(readOnly = true)
@Tag(name = "6. Suất chiếu (Showtimes)", description = "Lịch chiếu theo phòng/phim — query `cinemaId` tùy chọn.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
@SuppressWarnings("null")
public class ShowtimeController {
    private static final long PENDING_SEAT_HOLD_MINUTES = 5;

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final TicketRepository ticketRepository;
    private final CinemaScopeService cinemaScopeService;

    public ShowtimeController(
            ShowtimeRepository showtimeRepository,
            MovieRepository movieRepository,
            RoomRepository roomRepository,
            TicketRepository ticketRepository,
            CinemaScopeService cinemaScopeService) {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
        this.roomRepository = roomRepository;
        this.ticketRepository = ticketRepository;
        this.cinemaScopeService = cinemaScopeService;
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int BUSINESS_START_MINUTE = 7 * 60;
    private static final int BUSINESS_END_MINUTE = 25 * 60;
    private static final int MIN_SHOWTIME_LEAD_MINUTES = 60;

    @GetMapping
    @Operation(summary = "Danh sách suất chiếu")
    public ResponseEntity<ApiResponse<List<ShowtimeSlotResponse>>> getShowtimes(
            @Parameter(description = "Lọc theo rạp") @RequestParam(required = false) Integer cinemaId,
            @Parameter(description = "Lọc theo phim") @RequestParam(required = false) Integer movieId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q) {
        List<Showtime> showtimes;
        if (movieId != null && cinemaId != null) {
            showtimes = showtimeRepository.findByMovie_MovieIdAndRoom_Cinema_CinemaId(movieId, cinemaId);
        } else if (movieId != null) {
            showtimes = showtimeRepository.findByMovie_MovieId(movieId);
        } else if (cinemaId != null) {
            showtimes = showtimeRepository.findByRoom_Cinema_CinemaId(cinemaId);
        } else {
            showtimes = showtimeRepository.findAll();
        }

        LocalDateTime now = nowInAppZone();
        LocalDate maxDate = now.toLocalDate().plusDays(7);

        String needle = SearchUtils.pick(search, keyword, q);

        List<ShowtimeSlotResponse> slotList = showtimes.stream()
                .filter(s -> s.getStartTime() != null)
                .filter(s -> !s.getStartTime().toLocalDate().isBefore(now.toLocalDate()))
                .filter(s -> !s.getStartTime().toLocalDate().isAfter(maxDate))
                .filter(s -> SearchUtils.isBlank(needle) || SearchUtils.matches(needle,
                        s.getMovie() != null ? s.getMovie().getTitle() : null,
                        s.getRoom() != null ? s.getRoom().getName() : null,
                        s.getRoom() != null && s.getRoom().getCinema() != null ? s.getRoom().getCinema().getName() : null))
                .map(s -> {
                    List<Integer> held = ticketRepository.findHeldSeatIdsByShowtime(
                            s.getShowtimeId(),
                            now.minusMinutes(PENDING_SEAT_HOLD_MINUTES));
                    return toDTO(s, now, held);
                })
                .collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.<List<ShowtimeSlotResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách suất chiếu thành công")
                .data(slotList)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết suất chiếu")
    public ResponseEntity<ApiResponse<ShowtimeSlotResponse>> getShowtimeById(@PathVariable Integer id) {
        Showtime s = showtimeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy suất chiếu với id: " + id));

        LocalDateTime now = nowInAppZone();
        List<Integer> bookedSeatIds = ticketRepository.findHeldSeatIdsByShowtime(
                id,
                now.minusMinutes(PENDING_SEAT_HOLD_MINUTES));

        return ResponseEntity.ok(ApiResponse.<ShowtimeSlotResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy thông tin suất chiếu thành công")
                .data(toDTO(s, now, bookedSeatIds))
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo suất chiếu")
    @Transactional
    public ResponseEntity<ApiResponse<Integer>> createShowtime(@Valid @RequestBody ShowtimeRequest request) {
        validate(request);

        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phim với mã: " + request.getMovieId()));

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng với mã: " + request.getRoomId()));
        cinemaScopeService.requireCinemaAccess(room.getCinema());
        cinemaScopeService.requireCinemaOperational(room.getCinema());

        Showtime s = new Showtime();
        s.setMovie(movie);
        s.setRoom(room);
        s.setStartTime(request.getStartTime());
        s.setSurcharge(request.getSurcharge());

        assertShowtimeTimeAllowed(movie, request.getStartTime());
        assertNoRoomOverlap(null, room, movie, request.getStartTime());

        Showtime created = showtimeRepository.save(s);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Integer>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo suất chiếu thành công")
                .data(created.getShowtimeId())
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật suất chiếu")
    @Transactional
    public ResponseEntity<ApiResponse<Integer>> updateShowtime(@PathVariable Integer id, @Valid @RequestBody ShowtimeRequest request) {
        validate(request);

        Showtime s = showtimeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy suất chiếu với id: " + id));
        if (s.getRoom() != null) {
            cinemaScopeService.requireCinemaAccess(s.getRoom().getCinema());
            cinemaScopeService.requireCinemaOperational(s.getRoom().getCinema());
        }

        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phim với mã: " + request.getMovieId()));

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng với mã: " + request.getRoomId()));
        cinemaScopeService.requireCinemaAccess(room.getCinema());
        cinemaScopeService.requireCinemaOperational(room.getCinema());

        s.setMovie(movie);
        s.setRoom(room);
        s.setStartTime(request.getStartTime());
        s.setSurcharge(request.getSurcharge());

        assertShowtimeTimeAllowed(movie, request.getStartTime());
        assertNoRoomOverlap(id, room, movie, request.getStartTime());

        Showtime updated = showtimeRepository.save(s);

        return ResponseEntity.ok(ApiResponse.<Integer>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật suất chiếu thành công")
                .data(updated.getShowtimeId())
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa suất chiếu")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteShowtime(@PathVariable Integer id) {
        Showtime s = showtimeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy suất chiếu với id: " + id));
        if (s.getRoom() != null) {
            cinemaScopeService.requireCinemaAccess(s.getRoom().getCinema());
            cinemaScopeService.requireCinemaOperational(s.getRoom().getCinema());
        }
        showtimeRepository.delete(s);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa suất chiếu thành công")
                .build());
    }

    private ShowtimeSlotResponse toDTO(Showtime s, LocalDateTime now, List<Integer> bookedSeatIds) {
        Movie movie = s.getMovie();
        Room room = s.getRoom();
        Double basePrice = movie != null ? movie.getBasePrice() : 0.0;
        Double surcharge = s.getSurcharge() != null ? s.getSurcharge() : 0.0;
        Double price = basePrice + surcharge;

        String status = "Đã chiếu";
        if (s.getStartTime() != null) {
            int durationMin = movie != null && movie.getDuration() != null ? movie.getDuration() : 120;
            LocalDateTime end = s.getStartTime().plusMinutes(durationMin);
            if (now.isBefore(s.getStartTime())) {
                status = "Sắp chiếu";
            } else if (now.isBefore(end)) {
                status = "Đang chiếu";
            }
        }

        String date = s.getStartTime() != null ? s.getStartTime().toLocalDate().toString() : null;
        String time = s.getStartTime() != null ? s.getStartTime().toLocalTime().format(TIME_FMT) : null;

        String endTimeStr = null;
        if (s.getStartTime() != null) {
            int durationMin = movie != null && movie.getDuration() != null ? movie.getDuration() : 120;
            endTimeStr = s.getStartTime().plusMinutes(durationMin).toLocalTime().format(TIME_FMT);
        }

        ShowtimeSlotResponse dto = new ShowtimeSlotResponse();
        dto.setId(s.getShowtimeId());
        dto.setDate(date);
        dto.setTime(time);
        dto.setEndTime(endTimeStr);
        dto.setMovieId(movie != null ? movie.getMovieId() : null);
        dto.setMovieTitle(movie != null ? movie.getTitle() : null);
        dto.setRoomId(room != null ? room.getRoomId() : null);
        dto.setRoomName(room != null ? room.getName() : null);
        if (room != null && room.getCinema() != null) {
            dto.setCinemaId(room.getCinema().getCinemaId());
            dto.setCinemaName(room.getCinema().getName());
        }
        dto.setSurcharge(surcharge);
        dto.setBasePrice(basePrice);
        dto.setPrice(price);
        dto.setStatus(status);
        if (bookedSeatIds != null) {
            dto.setBookedSeatIds(bookedSeatIds);
            dto.setSoldTicketsCount(bookedSeatIds.size());
        } else {
            dto.setSoldTicketsCount(0);
        }
        return dto;
    }

    private void validate(ShowtimeRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu suất chiếu không hợp lệ");
        }
        if (request.getMovieId() == null || request.getRoomId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn phim và phòng");
        }
        if (request.getStartTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn thời gian suất chiếu");
        }
        if (request.getSurcharge() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập Phụ thu");
        }
        if (request.getSurcharge() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phụ thu không được nhỏ hơn 0");
        }
    }

    private LocalDateTime nowInAppZone() {
        return LocalDateTime.now(APP_ZONE);
    }

    private void assertShowtimeTimeAllowed(Movie movie, LocalDateTime start) {
        if (start == null) return;
        LocalDateTime now = nowInAppZone();
        if (start.isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể tạo hoặc cập nhật suất chiếu vào thời gian đã qua");
        }
        if (start.isBefore(now.plusMinutes(MIN_SHOWTIME_LEAD_MINUTES))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suất chiếu phải bắt đầu sau thời điểm hiện tại ít nhất 1 giờ");
        }
        int durationMin = movie != null && movie.getDuration() != null ? movie.getDuration() : 120;
        int startMinute = start.getHour() * 60 + start.getMinute();
        int businessMinute = startMinute < BUSINESS_START_MINUTE ? startMinute + 24 * 60 : startMinute;
        int endMinute = businessMinute + durationMin;
        if (businessMinute < BUSINESS_START_MINUTE || endMinute > BUSINESS_END_MINUTE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Suất chiếu phải nằm trong khung 07:00 - 01:00 và không được kết thúc sau 01:00");
        }
    }

    private void assertNoRoomOverlap(Integer excludeShowtimeId, Room room, Movie movie, LocalDateTime start) {
        if (room == null || start == null) return;
        int durationMin = movie != null && movie.getDuration() != null ? movie.getDuration() : 120;
        LocalDateTime newEnd = start.plusMinutes(durationMin);
        List<Showtime> inRoom = showtimeRepository.findByRoom_RoomId(room.getRoomId());
        for (Showtime ot : inRoom) {
            if (excludeShowtimeId != null && excludeShowtimeId.equals(ot.getShowtimeId())) continue;
            if (ot.getStartTime() == null) continue;
            Movie om = ot.getMovie();
            int od = om != null && om.getDuration() != null ? om.getDuration() : 120;
            LocalDateTime otEnd = ot.getStartTime().plusMinutes(od);
            if (start.isBefore(otEnd) && ot.getStartTime().isBefore(newEnd)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Trùng lịch trong phòng này — chọn giờ khác hoặc phòng khác.");
            }
        }
    }
}
