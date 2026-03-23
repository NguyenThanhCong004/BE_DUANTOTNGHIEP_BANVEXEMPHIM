# 🍿 BE_DUANTOTNGHIEP_BANVEXEMPHIM (SPRING BOOT REST API)

Backend bán vé xem phim — REST dưới prefix **`/api/v1`**, tài liệu đầy đủ trên **Swagger**.

## 🚀 Chạy nhanh
1. JDK 17, Maven; cấu hình `src/main/resources/application.properties` (MySQL, v.v.).
2. Chạy `BeDuantotnghiepBanvexemphimApplication.java` hoặc `mvn spring-boot:run`.
3. **API:** `http://localhost:8080`  
4. **Swagger UI:** `http://localhost:8080/swagger-ui.html`  
5. **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

## 📦 Gói chính
Spring Boot **3.5.11**, Spring Security + **JWT**, Spring Data JPA (**MySQL**), SpringDoc OpenAPI, PayOS, Mail, Cloudinary (theo `pom.xml`).

## 📂 Controller
Package `com.fpoly.duan.controller` — **23** `@RestController` (auth, users, staff, movies, cinemas, showtimes, seats, PayOS, `/me`, …). Chi tiết bảng mapping: **`backend.md`** ở thư mục gốc repo `JAVA6`.

---
*Tham chiếu thêm: `README.md`, `KET_NOI_BE_FE.md`, `API_DOCUMENTATION.md`, `doc.md` (cùng thư mục gốc).*
