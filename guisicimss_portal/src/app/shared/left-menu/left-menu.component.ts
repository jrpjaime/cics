import { Component, EventEmitter, Output, OnInit, OnDestroy } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { SharedService } from '../services/shared.service';

export interface MenuItem {
  name: string;
  icon: string;
  route?: string;
  isExpanded?: boolean;
  children?: MenuItem[];
  roles?: string[];
  disabled?: boolean;
}

@Component({
  selector: 'app-left-menu',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './left-menu.component.html',
  styleUrls: ['./left-menu.component.css']
})
export class LeftMenuComponent implements OnInit, OnDestroy {
  @Output() toggleMenuClicked = new EventEmitter<void>();

  private fullMenuItems: MenuItem[] = [
    {
      name: 'Gestionar Seguridad',
      icon: 'bi bi-shield-lock',
      isExpanded: true, // Forzamos inicio expandido para evitar que "desaparezca"
      roles: ['ADMIN'],
      children: [
        { name: 'Usuarios', icon: '', route: '/admin/usuarios' }
      ]
    },
    {
      name: 'Monitorización',
      icon: 'bi bi-speedometer2',
      isExpanded: true,
      roles: ['ADMIN'],
      children: [
        { name: 'Dashboard', icon: '', route: '/admin/dashboard' }
      ]
    }
  ];

  menuItems: MenuItem[] = [];
  private rolesSubscription!: Subscription;

  constructor(private router: Router, private sharedService: SharedService) {}

  ngOnInit(): void {
    // Escuchamos los roles
    this.rolesSubscription = this.sharedService.currentRoleSesion.subscribe(userRoles => {
      this.filterMenuItems(userRoles);
    });
  }

  ngOnDestroy(): void {
    if (this.rolesSubscription) this.rolesSubscription.unsubscribe();
  }

  private filterMenuItems(userRoles: string[]): void {
    // Si no hay roles aún, mostramos todo por defecto para evitar el efecto "vacío"
    // (o puedes poner una lógica de carga si prefieres)
    const roles = userRoles && userRoles.length > 0 ? userRoles : ['ADMIN'];

    this.menuItems = this.fullMenuItems.filter(item => {
      if (!item.roles) return true;
      return item.roles.some(role => roles.includes(role));
    });
  }

  onToggleMenu(): void {
    this.toggleMenuClicked.emit();
  }

  onItemClick(event: MouseEvent, item: MenuItem): void {
    // Si es un item deshabilitado
    if (item.disabled) {
      event.preventDefault();
      return;
    }

    // Si tiene hijos, solo expandimos/colapsamos el padre
    if (item.children && item.children.length > 0) {
      event.preventDefault();
      item.isExpanded = !item.isExpanded;
    }
    // Si es un hijo (no tiene children), dejamos que el routerLink actúe normal
  }
}
