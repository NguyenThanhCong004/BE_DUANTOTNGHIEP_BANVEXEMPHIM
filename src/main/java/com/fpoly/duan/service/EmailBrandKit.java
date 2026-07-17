package com.fpoly.duan.service;

/**
 * Khung HTML thương hiệu MovieZone dùng chung cho mọi email gửi đi (OTP, tài khoản nhân viên mới, vé điện tử...),
 * để tất cả email đều có logo/màu sắc/footer nhất quán thay vì mỗi nơi tự dựng HTML rời rạc.
 */
public final class EmailBrandKit {

    private EmailBrandKit() {
    }

    /**
     * Bọc nội dung {@code bodyHtml} (không cần có thẻ html/body) trong khung header/footer thương hiệu.
     *
     * @param preheader đoạn xem trước ẩn (hiện trong danh sách inbox của một số ứng dụng mail)
     * @param bodyHtml  nội dung chính, đã là HTML hợp lệ (thẻ p/div/table...)
     */
    public static String wrap(String preheader, String bodyHtml) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;background:#0d0e28;font-family:'Segoe UI',Arial,sans-serif;">
                  <span style="display:none;font-size:1px;color:#0d0e28;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">%s</span>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#0d0e28;padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px;background:#15163f;border-radius:16px;overflow:hidden;border:1px solid rgba(255,255,255,0.08);">
                          <tr>
                            <td style="background:linear-gradient(135deg,#8b00ff,#ff2d78);padding:26px 32px;">
                              <span style="font-family:Arial,sans-serif;font-size:24px;font-weight:800;letter-spacing:2px;color:#ffffff;">MOVIE<span style="color:#d4ff00;">ZONE</span></span>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:32px;color:#f0f0ff;font-size:14px;line-height:1.65;">
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 32px;background:#101130;border-top:1px solid rgba(255,255,255,0.06);">
                              <p style="margin:0;font-size:12px;color:rgba(240,240,255,0.45);">MovieZone &middot; Cái Răng, Cần Thơ, Việt Nam &middot; Hotline 0916 178 534</p>
                              <p style="margin:4px 0 0;font-size:12px;color:rgba(240,240,255,0.3);">Đây là email tự động, vui lòng không trả lời trực tiếp email này.</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(preheader == null ? "" : preheader, bodyHtml);
    }

    /** Nút bấm CTA thương hiệu (gradient tím-hồng), dùng trong {@code bodyHtml}. */
    public static String button(String href, String label) {
        return """
                <div style="text-align:center;margin:24px 0 4px;">
                  <a href="%s" style="display:inline-block;padding:13px 32px;border-radius:10px;background:linear-gradient(135deg,#8b00ff,#ff2d78);color:#ffffff;text-decoration:none;font-weight:700;font-size:14px;letter-spacing:0.5px;">%s</a>
                </div>
                """.formatted(href, label);
    }
}
