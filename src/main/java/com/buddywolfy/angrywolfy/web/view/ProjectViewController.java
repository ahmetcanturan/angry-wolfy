package com.buddywolfy.angrywolfy.web.view;

import com.buddywolfy.angrywolfy.entity.Config;
import com.buddywolfy.angrywolfy.service.ConfigService;
import com.buddywolfy.angrywolfy.service.ProjectService;
import com.buddywolfy.angrywolfy.service.TargetService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/projects")
public class ProjectViewController {

    private final ProjectService projectService;
    private final ConfigService configService;
    private final TargetService targetService;

    public ProjectViewController(ProjectService projectService, ConfigService configService,
                                  TargetService targetService) {
        this.projectService = projectService;
        this.configService = configService;
        this.targetService = targetService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("projects", projectService.getAll());
        return "projects/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("project", new ProjectForm());
        return "projects/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("project") ProjectForm form,
                          BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "projects/form";
        }
        var project = projectService.create(form.getName(), form.getDescription(), form.getRepositoryPath());
        return "redirect:/projects/" + project.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                          @RequestParam(required = false) Long config,
                          Model model) {
        var project = projectService.getById(id);
        List<Config> configs = configService.getByProjectId(id);

        Config activeConfig = null;
        if (!configs.isEmpty()) {
            activeConfig = configs.stream()
                    .filter(c -> c.getId().equals(config))
                    .findFirst()
                    .orElse(configs.get(0));
        }

        model.addAttribute("project", project);
        model.addAttribute("configs", configs);
        model.addAttribute("targets", targetService.getByProjectId(id));
        model.addAttribute("activeConfigId", activeConfig != null ? activeConfig.getId() : null);
        model.addAttribute("activeConfig", activeConfig);
        return "projects/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var project = projectService.getById(id);
        model.addAttribute("projectId", project.getId());
        model.addAttribute("project", new ProjectForm(
                project.getName(), project.getDescription(), project.getRepositoryPath()));
        return "projects/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                          @Valid @ModelAttribute("project") ProjectForm form,
                          BindingResult bindingResult,
                          Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("projectId", id);
            return "projects/form";
        }
        projectService.update(id, form.getName(), form.getDescription(), form.getRepositoryPath());
        return "redirect:/projects/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        projectService.delete(id);
        return "redirect:/projects";
    }
}
