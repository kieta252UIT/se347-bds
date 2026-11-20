package com.realestates.backend.listing.infrastructure;

import com.realestates.backend.listing.domain.HanMucThangHelper;
import com.realestates.backend.listing.domain.NoiDungTinDauVao;
import com.realestates.backend.listing.domain.QuyTacQuanLyTin;
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

    @Override
    public synchronized Optional<UUID> khoaVaDocChuTin(UUID tinId) {
        if (tinId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tinTheoId.get(tinId)).map(TinLuuTru::chuId);
    }

    @Override
    public synchronized TinGhiKetQua suaTinTrongTransaction(DuLieuSuaTin duLieu) {
        String khoaYeuCau = khoaYeuCau(duLieu.nguoiSuaId(), LOAI_THAO_TAC_SUA_TIN, duLieu.maYDinh());
        KetQuaYeuCauGhi daLuu = yeuCauGhiTheoKhoa.get(khoaYeuCau);
        if (daLuu != null) {
            return phatLaiKetQuaDaGhi(daLuu, duLieu.duLieuBam());
        }

        TinLuuTru tin = docTinHoacBaoLoi(duLieu.tinId());
        kiemTraDieuKienGhi(
                QuyTacQuanLyTin.dungPhienBanDaXem(
                        tin.phienBanCapNhat(),
                        duLieu.phienBanCapNhatDaXem(),
                        tin.phienBanHienTaiId(),
                        duLieu.phienBanDaXemId()
                ),
                QuyTacQuanLyTin.choPhepSua(tin.trangThai())
        );
        boolean anhHopLe = anhTinRepository.kiemTraAnhChoSuaTin(
                duLieu.nguoiSuaId(),
                phienBanId -> {
                    PhienBanLuuTru phienBan = phienBanTheoId.get(phienBanId);
                    return phienBan != null && phienBan.tinId().equals(duLieu.tinId());
                },
                duLieu.anhIds()
        );
        if (!anhHopLe) {
            throw new LoiNghiepVuQuanLyTinException(
                    LoaiLoiQuanLyTin.ANH_KHONG_HOP_LE,
                    "Danh sách ảnh chứa mã ảnh không tồn tại, không thuộc chủ tin hoặc không được phép dùng lại."
            );
        }

        UUID phienBanMoiId = UUID.randomUUID();
        int soPhienBanMoi = phienBanTheoId.values().stream()
                .filter(pb -> pb.tinId().equals(duLieu.tinId()))
                .mapToInt(PhienBanLuuTru::soPhienBan)
                .max()
                .orElse(0) + 1;
        anhTinRepository.ganAnhChoPhienBanSua(duLieu.nguoiSuaId(), phienBanMoiId, duLieu.anhIds());

        long phienBanCapNhatMoi = tin.phienBanCapNhat() + 1;
        phienBanTheoId.put(phienBanMoiId, new PhienBanLuuTru(
                phienBanMoiId,
                duLieu.tinId(),
                soPhienBanMoi,
                duLieu.noiDung(),
                List.copyOf(duLieu.anhIds()),
                duLieu.ketQuaSoBo(),
                TRANG_THAI_CHO_KIEM_DUYET,
                duLieu.thoiDiem()
        ));
        tinTheoId.put(duLieu.tinId(), new TinLuuTru(
                tin.id(),
                tin.chuId(),
                TRANG_THAI_CHO_KIEM_DUYET,
                tin.ngayDang(),
                phienBanMoiId,
                tin.phienBanDaDuyetId(),
                phienBanCapNhatMoi
        ));

        TinGhiKetQua ketQua = new TinGhiKetQua(duLieu.tinId(), phienBanMoiId, TRANG_THAI_CHO_KIEM_DUYET, phienBanCapNhatMoi);
        yeuCauGhiTheoKhoa.put(khoaYeuCau, new KetQuaYeuCauGhi(duLieu.duLieuBam(), ketQua));
        ghiAudit(
                duLieu.nguoiSuaId(),
                duLieu.tinId(),
                phienBanMoiId,
                LOAI_THAO_TAC_SUA_TIN,
                QuyTacQuanLyTin.duLieuAuditSua(
                        tin.trangThai(),
                        tin.phienBanHienTaiId(),
                        tin.phienBanCapNhat(),
                        phienBanMoiId,
                        soPhienBanMoi,
                        phienBanCapNhatMoi
                ),
                duLieu.thoiDiem()
        );
        return ketQua;
    }

    @Override
    public synchronized TinGhiKetQua xoaTinTrongTransaction(DuLieuXoaTin duLieu) {
        String khoaYeuCau = khoaYeuCau(duLieu.nguoiXoaId(), LOAI_THAO_TAC_XOA_TIN, duLieu.maYDinh());
        KetQuaYeuCauGhi daLuu = yeuCauGhiTheoKhoa.get(khoaYeuCau);
        if (daLuu != null) {
            return phatLaiKetQuaDaGhi(daLuu, duLieu.duLieuBam());
        }

        TinLuuTru tin = docTinHoacBaoLoi(duLieu.tinId());
        kiemTraDieuKienGhi(
                QuyTacQuanLyTin.dungPhienBanCapNhat(tin.phienBanCapNhat(), duLieu.phienBanCapNhatDaXem()),
                QuyTacQuanLyTin.choPhepXoa(tin.trangThai())
        );

        long phienBanCapNhatMoi = tin.phienBanCapNhat() + 1;
        tinTheoId.put(duLieu.tinId(), new TinLuuTru(
                tin.id(),
                tin.chuId(),
                TRANG_THAI_DA_XOA,
                tin.ngayDang(),
                tin.phienBanHienTaiId(),
                tin.phienBanDaDuyetId(),
                phienBanCapNhatMoi
        ));

        TinGhiKetQua ketQua = new TinGhiKetQua(
                duLieu.tinId(),
                tin.phienBanHienTaiId(),
                TRANG_THAI_DA_XOA,
                phienBanCapNhatMoi
        );
        yeuCauGhiTheoKhoa.put(khoaYeuCau, new KetQuaYeuCauGhi(duLieu.duLieuBam(), ketQua));
        ghiAudit(
                duLieu.nguoiXoaId(),
                duLieu.tinId(),
                tin.phienBanHienTaiId(),
                LOAI_THAO_TAC_XOA_TIN,
                QuyTacQuanLyTin.duLieuAuditXoa(tin.trangThai(), tin.phienBanCapNhat(), phienBanCapNhatMoi),
                duLieu.thoiDiem()
        );
        return ketQua;
    }

    public synchronized void datTrangThaiTinChoTest(UUID tinId, String trangThai, UUID phienBanDaDuyetId) {
        TinLuuTru tin = docTinHoacBaoLoi(tinId);
        tinTheoId.put(tinId, new TinLuuTru(
                tin.id(),
                tin.chuId(),
                trangThai,
                tin.ngayDang(),
                tin.phienBanHienTaiId(),
                phienBanDaDuyetId,
                tin.phienBanCapNhat()
        ));
    }

    private TinLuuTru docTinHoacBaoLoi(UUID tinId) {
        TinLuuTru tin = tinTheoId.get(tinId);
        if (tin == null) {
            throw new LoiNghiepVuQuanLyTinException(LoaiLoiQuanLyTin.TIN_KHONG_TON_TAI, "Không tìm thấy tin.");
        }
        return tin;
    }

    private static TinGhiKetQua phatLaiKetQuaDaGhi(KetQuaYeuCauGhi daLuu, String duLieuBam) {
        if (!daLuu.duLieuBam().equals(duLieuBam)) {
            throw new LoiNghiepVuQuanLyTinException(
                    LoaiLoiQuanLyTin.XUNG_DOT_MA_Y_DINH,
                    "Mã Idempotency-Key đã được sử dụng cho một nội dung yêu cầu khác."
            );
        }
        return daLuu.ketQua();
    }

    private static void kiemTraDieuKienGhi(boolean dungPhienBan, boolean trangThaiChoPhep) {
        if (!dungPhienBan) {
            throw new LoiNghiepVuQuanLyTinException(
                    LoaiLoiQuanLyTin.PHIEN_BAN_CU,
                    "Tin đã thay đổi. Hãy tải lại trước khi thao tác."
            );
        }
        if (!trangThaiChoPhep) {
            throw new LoiNghiepVuQuanLyTinException(
                    LoaiLoiQuanLyTin.TRANG_THAI_KHONG_HOP_LE,
                    "Trạng thái hiện tại của tin không cho phép thao tác này."
            );
        }
    }

    private void ghiAudit(
            UUID nguoiThucHienId,
            UUID tinId,
            UUID phienBanId,
            String hanhDong,
            String duLieuAuditJson,
            Instant thoiDiem
    ) {
        UUID auditId = UUID.randomUUID();
        auditTheoId.put(auditId, new AuditLuuTru(
                auditId,
                nguoiThucHienId,
                tinId,
                phienBanId,
                hanhDong,
                duLieuAuditJson,
                thoiDiem
        ));
    }

    private static String khoaYeuCau(UUID taiKhoanId, String loaiThaoTac, String maYDinh) {
        return taiKhoanId + "|" + loaiThaoTac + "|" + maYDinh;
    }

    private static String khoaHanMuc(UUID taiKhoanId, LocalDate dauThang) {
        return taiKhoanId + "|" + dauThang;
    }
}
