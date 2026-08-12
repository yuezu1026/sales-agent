package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.entity.PromptTemplate;
import com.aicustomer.repository.PromptTemplateRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Prompt 模板管理（D3）：按场景 CRUD，内容变更版本号自增
 */
@RestController
@RequestMapping("/api/prompt-templates")
public class PromptTemplateController {

    private final PromptTemplateRepository repository;

    public PromptTemplateController(PromptTemplateRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<PromptTemplate>> list() {
        return ApiResponse.ok(repository.findAll());
    }

    @PostMapping
    public ApiResponse<PromptTemplate> create(@Valid @RequestBody UpsertRequest request) {
        if (repository.findByScene(request.scene()).isPresent()) {
            throw new IllegalArgumentException("场景 " + request.scene() + " 的模板已存在");
        }
        PromptTemplate t = new PromptTemplate();
        t.setScene(request.scene());
        t.setName(request.name());
        t.setContent(request.content());
        t.setVersion(1);
        t.setEnabled(true);
        t.setUpdatedAt(LocalDateTime.now());
        t.setTenantId(com.aicustomer.common.TenantContext.require());
        return ApiResponse.ok(repository.save(t));
    }

    @PutMapping("/{id}")
    public ApiResponse<PromptTemplate> update(@PathVariable Long id,
                                              @Valid @RequestBody UpsertRequest request) {
        PromptTemplate t = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在"));
        // 内容/名称变化 → 版本号自增
        boolean changed = !t.getContent().equals(request.content()) || !t.getName().equals(request.name());
        t.setName(request.name());
        t.setContent(request.content());
        if (changed) {
            t.setVersion(t.getVersion() + 1);
        }
        t.setUpdatedAt(LocalDateTime.now());
        return ApiResponse.ok(repository.save(t));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("模板不存在");
        }
        repository.deleteById(id);
        return ApiResponse.ok(null);
    }

    public record UpsertRequest(
            @NotBlank(message = "场景不能为空") String scene,
            @NotBlank(message = "名称不能为空") String name,
            @NotBlank(message = "内容不能为空") String content) {
    }
}
