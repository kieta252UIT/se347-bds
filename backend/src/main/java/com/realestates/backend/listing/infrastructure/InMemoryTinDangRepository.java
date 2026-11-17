package com.realestates.backend.listing.infrastructure;

import com.realestates.backend.listing.domain.HanMucThangHelper;
import com.realestates.backend.listing.domain.NoiDungTinDauVao;
import com.realestates.backend.listing.domain.TinDangRepository;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryTinDangRepository implements TinDangRepository {

    public record TinLuuTru(
            UUID id,
            UUID chuId,
            String trangThai,
            Instant ngayDang,
            UUID phienBanHienTaiId,
            UUID phienBanDaDuyetId,
            long phienBanCapNhat
    ) {
    }

    public record PhienBanLuuTru(
            UUID id,
            UUID tinId,
            int soPhienBan,
            NoiDungTinDauVao noiDung,
            List<UUID> anhIds,
            String ketQuaSoBo,
            String ketQuaKiemDuyet,
            Instant taoLuc
    ) {
    }

    public record AuditLuuTru(
            UUID id,
            UUID nguoiThucHienId,
            UUID tinId,
            UUID phienBanTinId,
            String hanhDong,
            String duLieuTruocSauJson,
            Instant thoiDiem
    ) {
    }

    private final InMemoryAnhTinRepository anhTinRepository;
    private final ConcurrentMap<UUID, TinLuuTru> tinTheoId = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, PhienBanLuuTru> phienBanTheoId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, KetQuaYeuCauGhi> yeuCauGhiTheoKhoa = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> hanMucTheoThang = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, AuditLuuTru> auditTheoId = new ConcurrentHashMap<>();

    public InMemoryTinDangRepository(InMemoryAnhTinRepository anhTinRepository) {
        this.anhTinRepository = Objects.requireNonNull(anhTinRepository, "anhTinRepository không được null");
    }

    @Override
    public synchronized Optional<KetQuaYeuCauGhi> timYeuCauGhi(UUID taiKhoanId, String loaiThaoTac, String maYDinh) {
        if (taiKhoanId == null || loaiThaoTac == null || maYDinh == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(yeuCauGhiTheoKhoa.get(khoaYeuCau(taiKhoanId, loaiThaoTac, maYDinh)));
    }

    @Override
    public synchronized TinGhiKetQua taoTinMoiTrongTransaction(DuLieuTaoTin duLieu) {
        String khoaYeuCau = khoaYeuCau(duLieu.nguoiDangId(), LOAI_THAO_TAC_DANG_TIN, duLieu.maYDinh());
        KetQuaYeuCauGhi daLuu = yeuCauGhiTheoKhoa.get(khoaYeuCau);
        if (daLuu != null) {
            if (!daLuu.duLieuBam().equals(duLieu.duLieuBam())) {
                throw new LoiNghiepVuTaoTinException(
                        LoaiLoiTaoTin.XUNG_DOT_MA_Y_DINH,
                        "Mã Idempotency-Key đã được sử dụng cho một nội dung yêu cầu khác."
                );
            }
            return daLuu.ketQua();
        }

        String khoaHanMuc = khoaHanMuc(duLieu.nguoiDangId(), duLieu.thangVietNam());
        int soTinHienTai = hanMucTheoThang.getOrDefault(khoaHanMuc, 0);
        if (soTinHienTai >= HanMucThangHelper.GIOI_HAN_TIN_MOI_MOI_THANG) {
            throw new LoiNghiepVuTaoTinException(
                    LoaiLoiTaoTin.VUOT_HAN_MUC_THANG,
                    "Tài khoản đã đạt hạn mức tối đa 3 tin mới trong tháng."
            );
        }

        if (!anhTinRepository.kiemTraTatCaAnhTamHopLeChoChu(duLieu.nguoiDangId(), duLieu.anhIds())) {
            throw new LoiNghiepVuTaoTinException(
                    LoaiLoiTaoTin.ANH_KHONG_HOP_LE,
                    "Danh sách ảnh chứa mã ảnh không tồn tại, không ở trạng thái tạm hoặc không thuộc chủ tài khoản."
            );
        }

        UUID tinId = UUID.randomUUID();
        UUID phienBanId = UUID.randomUUID();
        TinGhiKetQua ketQua = new TinGhiKetQua(tinId, phienBanId, TRANG_THAI_CHO_KIEM_DUYET, 0L);

        anhTinRepository.ganAnhVaoPhienBanChoDuyet(duLieu.nguoiDangId(), phienBanId, duLieu.anhIds());
        hanMucTheoThang.put(khoaHanMuc, soTinHienTai + 1);
        tinTheoId.put(tinId, new TinLuuTru(
                tinId,
                duLieu.nguoiDangId(),
                TRANG_THAI_CHO_KIEM_DUYET,
                duLieu.thoiDiem(),
                phienBanId,
                null,
                0L
        ));
        phienBanTheoId.put(phienBanId, new PhienBanLuuTru(
                phienBanId,
                tinId,
                1,
                duLieu.noiDung(),
                List.copyOf(duLieu.anhIds()),
                duLieu.ketQuaSoBo(),
                TRANG_THAI_CHO_KIEM_DUYET,
                duLieu.thoiDiem()
        ));
        yeuCauGhiTheoKhoa.put(khoaYeuCau, new KetQuaYeuCauGhi(duLieu.duLieuBam(), ketQua));
        UUID auditId = UUID.randomUUID();
        auditTheoId.put(auditId, new AuditLuuTru(
                auditId,
                duLieu.nguoiDangId(),
                tinId,
                phienBanId,
                LOAI_THAO_TAC_DANG_TIN,
                duLieu.duLieuAuditJson(),
                duLieu.thoiDiem()
        ));
        return ketQua;
    }

    @Override
    public synchronized int docSoTinMoiTrongThang(UUID taiKhoanId, LocalDate dauThangVietNam) {
        if (taiKhoanId == null || dauThangVietNam == null) {
            return 0;
        }
        return hanMucTheoThang.getOrDefault(khoaHanMuc(taiKhoanId, dauThangVietNam), 0);
    }

    public synchronized void datHanMucThangChoTest(UUID taiKhoanId, LocalDate dauThangVietNam, int soTinMoi) {
        hanMucTheoThang.put(khoaHanMuc(taiKhoanId, dauThangVietNam), soTinMoi);
    }

    public Optional<TinLuuTru> timTinTheoId(UUID tinId) {
        return Optional.ofNullable(tinTheoId.get(tinId));
    }

    public Optional<PhienBanLuuTru> timPhienBanTheoId(UUID phienBanId) {
        return Optional.ofNullable(phienBanTheoId.get(phienBanId));
    }

    public List<AuditLuuTru> layAuditTheoTinId(UUID tinId) {
        return auditTheoId.values().stream()
                .filter(a -> a.tinId().equals(tinId))
                .toList();
    }

    private static String khoaYeuCau(UUID taiKhoanId, String loaiThaoTac, String maYDinh) {
        return taiKhoanId + "|" + loaiThaoTac + "|" + maYDinh;
    }

    private static String khoaHanMuc(UUID taiKhoanId, LocalDate dauThang) {
        return taiKhoanId + "|" + dauThang;
    }
}
