import { Component, OnInit } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { SharedService } from '../services/shared.service';
import { CommonModule } from '@angular/common';
import { BaseComponent } from '../base/base.component';

@Component({
    selector: 'app-sidebar',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './sidebar.component.html',
    styleUrl: './sidebar.component.css'
})
export class SidebarComponent extends BaseComponent implements OnInit {
  nombreCompletoDisplay: string = '';

  constructor(
    private authService: AuthService,
    sharedService: SharedService
  ) {
    super(sharedService);
  }

  logout(): void {
    this.authService.logout();
  }

  override ngOnInit(): void {
    super.ngOnInit();
    this.recargaParametros();

    this.nombreCompleto$.subscribe(nombre => {
      // Si no hay nombre, ponemos "Usuario" por defecto
      this.nombreCompletoDisplay = nombre && nombre.trim() !== '' ? nombre : 'Usuario';
    });
  }
}
