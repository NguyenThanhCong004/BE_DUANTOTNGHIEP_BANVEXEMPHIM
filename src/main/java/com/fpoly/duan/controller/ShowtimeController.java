package com.fpoly.duan.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class ShowtimeController {

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final TicketRepository ticketRepository;

    public ShowtimeController(
            ShowtimeRepository showtimeRepository,
            MovieRepository movieRepository,
            RoomRepository roomRepository,
            TicketRepository ticketRepository) {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
        this.roomRepository = roomRepository;
        this.ticketRepository = ticketRepository;
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @GetMapping
    @Operation(summary = "Danh sách suất chiếu")
    public ResponseEntity<ApiResponse<List<ShowtimeSlotResponse>>> getShowtimes(
            @Parameter(description = "Lọc theo rạp") @RequestParam(required = false) Integer cinemaId,
            @Parameter(description = "Lọc theo phim") @RequestParam(required = false) Integer movieId) {
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

        LocalDateTime now = LocalDateTime.now();
        LocalDate maxDate = now.toLocalDate().plusDays(7); // Chỉ lấy đến 7 ngày sau

        List<ShowtimeSlotResponse> slotList = showtimes.stream()
                .filter(s -> s.getStartTime() != null)
                .filter(s -> !s.getStartTime().toLocalDate().isBefore(now.toLocalDate())) // Từ hôm nay
                .filter(s -> !s.getStartTime().toLocalDate().isAfter(maxDate)) // Đến 7 ngày sau
                .map(s -> {
                    // Đếm vé đã bán/đang giữ
                    List<Integer> held = ticketRepository.findHeldSeatIdsByShowtime(s.getShowtimeId());
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
                .orElseThrow(() -> new RuntimeException("Không tìm thấy suất chiếu với id: " + id));

        List<Integer> bookedSeatIds = ticketRepository.findHeldSeatIdsByShowtime(id);

        return ResponseEntity.ok(ApiResponse.<ShowtimeSlotResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy thông tin suất chiếu thành công")
                .data(toDTO(s, LocalDateTime.now(), bookedSeatIds))
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo suất chiếu")
    @Transactional
    public ResponseEntity<ApiResponse<Integer>> createShowtime(@RequestBody ShowtimeRequest request) {
        validate(request);

        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phim với mã: " + request.getMovieId()));

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng với mã: " + request.getRoomId()));

        Showtime s = new Showtime();
        s.setMovie(movie);
        s.setRoom(room);
        s.setStartTime(request.getStartTime());
        s.setSurcharge(request.getSurcharge());

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
    public ResponseEntity<ApiResponse<Integer>> updateShowtime(@PathVariable Integer id, @RequestBody ShowtimeRequest request) {
        validate(request);

        Showtime s = showtimeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy suất chiếu với id: " + id));

        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phim với mã: " + request.getMovieId()));

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng với mã: " + request.getRoomId()));

        s.setMovie(movie);
        s.setRoom(room);
        s.setStartTime(request.getStartTime());
        s.setSurcharge(request.getSurcharge());

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
        if (!showtimeRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy suất chiếu với id: " + id);
        }
        showtimeRepository.deleteById(id);
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
            throw new RuntimeException("Dữ liệu suất chiếu không hợp lệ");
        }
        if (request.getMovieId() == null || request.getRoomId() == null) {
            throw new RuntimeException("Vui lòng chọn phim và phòng");
        }
        if (request.getStartTime() == null) {
            throw new RuntimeException("Vui lòng chọn thời gian suất chiếu");
        }
        if (request.getSurcharge() == null) {
            throw new RuntimeException("Vui lòng nhập Phụ thu");
        }
        if (request.getSurcharge() < 0) {
            throw new RuntimeException("Phụ thu không được nhỏ hơn 0");
        }
    }

    /** Không cho hai suất trong cùng phòng chồng lên nhau (theo thời lượng phim). */
    private void assertNoRoomOverlap(Integer excludeShowtimeId, Room room, Movie movie, LocalDateTime start) {
        if (room == null || start == null) {
            return;
        }
        int durationMin = movie != null && movie.getDuration() != null ? movie.getDuration() : 120;
        LocalDateTime newEnd = start.plusMinutes(durationMin);
        List<Showtime> inRoom = showtimeRepository.findByRoom_RoomId(room.getRoomId());
        for (Showtime ot : inRoom) {
            if (excludeShowtimeId != null && excludeShowtimeId.equals(ot.getShowtimeId())) {
                continue;
            }
            if (ot.getStartTime() == null) {
                continue;
            }
            Movie om = ot.getMovie();
            int od = om != null && om.getDuration() != null ? om.getDuration() : 120;
            LocalDateTime otEnd = ot.getStartTime().plusMinutes(od);
            if (start.isBefore(otEnd) && ot.getStartTime().isBefore(newEnd)) {
                throw new RuntimeException("Trùng lịch trong phòng này — chọn giờ khác hoặc phòng khác.");
            }
        }
    }
}

