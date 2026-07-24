package com.interview.platform.resume;

import com.interview.platform.common.BusinessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class ResumeService {
    private final ResumeRepository resumes;
    private final ResumeVersionRepository versions;

    public ResumeService(ResumeRepository resumes, ResumeVersionRepository versions) {
        this.resumes = resumes;
        this.versions = versions;
    }

    @Transactional
    public ResumeView create(String userId, CreateResume command) {
        Resume resume = resumes.saveAndFlush(new Resume(userId, command.title().trim(),
                command.targetRole().trim(), command.content().trim()));
        versions.save(new ResumeVersion(resume, "CREATED"));
        return toView(resume);
    }

    @Transactional
    public ResumeView upload(String userId, MultipartFile file) {
        byte[] bytes = validatedBytes(file);
        String filename = safeFilename(file.getOriginalFilename());
        String lower = filename.toLowerCase(Locale.ROOT);
        String content;
        try {
            if (lower.endsWith(".pdf")) content = parsePdf(bytes);
            else if (lower.endsWith(".docx")) content = parseDocx(bytes);
            else throw invalidFile("仅支持 PDF 或 DOCX 文件");
        } catch (IOException exception) {
            throw invalidFile("文件无法解析，可能已加密、损坏或不包含可提取文字");
        }
        content = normalizeExtractedText(content);
        if (content.length() < 30) throw invalidFile("提取文字过少，请上传文本型文件或改为手工填写");
        String title = filename.replaceFirst("(?i)\\.(pdf|docx)$", "");
        Resume resume = resumes.saveAndFlush(Resume.parsed(userId, filename, title,
                "待确认目标岗位", content, content.length() >= 300 ? 0.88 : 0.65));
        versions.save(new ResumeVersion(resume, "PARSED"));
        return toView(resume);
    }

    @Transactional
    public ResumeView confirm(String resumeId, String userId, ConfirmResume command) {
        Resume resume = owned(resumeId, userId);
        if (resume.getVersion() != command.version()) {
            throw BusinessException.conflict("RESUME_VERSION_CONFLICT", "简历已被更新，请刷新后重试");
        }
        resume.confirm(command.title().trim(), command.targetRole().trim(), command.content().trim());
        resumes.flush();
        versions.save(new ResumeVersion(resume, "CONFIRMED"));
        return toView(resume);
    }

    @Transactional
    public ResumeView makeDefault(String resumeId, String userId) {
        Resume selected = owned(resumeId, userId);
        if (!"CONFIRMED".equals(selected.getStatus())) {
            throw BusinessException.conflict("RESUME_NOT_CONFIRMED", "请先确认简历解析结果");
        }
        resumes.findByUserIdOrderByUpdatedAtDesc(userId).forEach(value -> {
            if (value.getId().equals(resumeId)) value.makeDefault(); else value.clearDefault();
        });
        return toView(selected);
    }

    @Transactional(readOnly = true)
    public List<ResumeView> list(String userId) {
        return resumes.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public ResumeSnapshot requireOwned(String id, String userId) {
        Resume resume = owned(id, userId);
        if (!"CONFIRMED".equals(resume.getStatus())) {
            throw BusinessException.conflict("RESUME_NOT_CONFIRMED", "简历解析结果确认后才能创建面试");
        }
        return new ResumeSnapshot(resume.getId(), resume.getTargetRole(), resume.getContent());
    }

    @Transactional(readOnly = true)
    public ResumeView get(String id, String userId) { return toView(owned(id, userId)); }

    @Transactional(readOnly = true)
    public List<ResumeVersionView> versions(String id, String userId) {
        owned(id, userId);
        return versions.findByResumeIdOrderByVersionNoDesc(id).stream().map(value ->
                new ResumeVersionView(value.getVersionNo(), value.getTitle(), value.getTargetRole(),
                        value.getContent(), value.getChangeType(), value.getCreatedAt())).toList();
    }

    private Resume owned(String id, String userId) {
        return resumes.findByIdAndUserId(id, userId)
                .orElseThrow(() -> BusinessException.notFound("简历不存在"));
    }

    private ResumeView toView(Resume resume) {
        return new ResumeView(resume.getId(), resume.getTitle(), resume.getTargetRole(),
                resume.getContent(), resume.getStatus(), resume.getSourceType(), resume.getOriginalFilename(),
                resume.getParseConfidence(), resume.getConfirmedAt(), resume.isDefault(),
                resume.getVersion(), resume.getCreatedAt(), resume.getUpdatedAt());
    }

    private byte[] validatedBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalidFile("请选择要上传的文件");
        if (file.getSize() > 5 * 1024 * 1024) throw invalidFile("文件不能超过 5MB");
        try {
            byte[] bytes = file.getBytes();
            String filename = safeFilename(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
            boolean pdf = filename.endsWith(".pdf") && bytes.length >= 5
                    && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
            boolean docx = filename.endsWith(".docx") && bytes.length >= 4
                    && bytes[0] == 'P' && bytes[1] == 'K';
            if (!pdf && !docx) throw invalidFile("文件扩展名与实际格式不一致");
            return bytes;
        } catch (IOException exception) {
            throw invalidFile("读取文件失败");
        }
    }

    private String parsePdf(byte[] bytes) throws IOException {
        try (var document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) throw new IOException("encrypted");
            return new PDFTextStripper().getText(document);
        }
    }

    private String parseDocx(byte[] bytes) throws IOException {
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getParagraphs().stream().map(value -> value.getText())
                    .collect(java.util.stream.Collectors.joining("\n"));
        }
    }

    private String normalizeExtractedText(String value) {
        String text = value == null ? "" : value.replace("\u0000", "").replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n").trim();
        return text.length() <= 20000 ? text : text.substring(0, 20000);
    }

    private String safeFilename(String value) {
        String name = value == null ? "" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.length() > 255) throw invalidFile("文件名不合法");
        return name;
    }

    private BusinessException invalidFile(String message) {
        return new BusinessException("INVALID_RESUME_FILE", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public record CreateResume(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 120) String targetRole,
            @NotBlank @Size(min = 30, max = 20000) String content) {}
    public record ConfirmResume(long version, @NotBlank @Size(max = 120) String title,
                                @NotBlank @Size(max = 120) String targetRole,
                                @NotBlank @Size(min = 30, max = 20000) String content) {}
    public record ResumeView(String id, String title, String targetRole, String content,
                             String status, String sourceType, String originalFilename,
                             Double parseConfidence, Instant confirmedAt, boolean isDefault,
                             long version, Instant createdAt, Instant updatedAt) {}
    public record ResumeVersionView(long version, String title, String targetRole, String content,
                                    String changeType, Instant createdAt) {}
    public record ResumeSnapshot(String id, String targetRole, String content) {}
}
