package com.hairsalonproject2.designer.repository;

import com.hairsalonproject2.designer.entity.Designer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignerRepository extends JpaRepository<Designer, Integer> {
}