import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';

import { BaseComponent } from '../../../shared/base/base.component';
import { SharedService } from '../../../shared/services/shared.service';
import { AdminService } from '../services/admin.service';
import { AlertService } from '../../../shared/services/alert.service';
import { LoaderService } from '../../../shared/services/loader.service';
import { PaginatorUiComponent } from '../../../shared/paginador/paginatorui/paginatorui-component';

@Component({
  selector: 'app-usuarios-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginatorUiComponent],
  templateUrl: './usuarios-admin.component.html'
})
export class UsuariosAdminComponent extends BaseComponent implements OnInit {

  // Inyecciones mediante inject() para mayor limpieza
  private router = inject(Router);
  private adminService = inject(AdminService);
  private alert = inject(AlertService);
  private loader = inject(LoaderService);

  usuarios: any[] = [];

  filtros = {
    userApi: '',
    userMain: '',
    rol: ''
  };

  public paginador = this.createPaginator(10);

  constructor(sharedService: SharedService) {
    super(sharedService);
  }

  override ngOnInit(): void {
    super.ngOnInit();
    this.cargarUsuarios(0);
  }

  cargarUsuarios(pagina: number) {
    // Forzamos el procesando al inicio
    this.loader.show();
    this.paginador.indicePaginaActual.set(pagina);

    this.adminService.listarUsuarios(pagina, this.paginador.tamanoPagina(), this.filtros)
      .pipe(finalize(() => this.loader.hide()))
      .subscribe({
        next: (res: any) => {
          this.usuarios = res.content || [];
          this.paginador.actualizarEstado(res);
        },
        error: (err) => {
          console.error("Error de comunicación:", err);
          this.alert.error("No se pudo conectar con el servidor central.");
        }
      });
  }

  limpiarFiltros() {
    this.filtros = { userApi: '', userMain: '', rol: '' };
    this.cargarUsuarios(0);
  }

  irANuevo() {
    this.router.navigate(['/admin/usuarios/nuevo']);
  }

  irAEditar(id: number) {
    // Al ir a editar, el componente detalle cargará la información
    this.router.navigate(['/admin/usuarios/editar', id]);
  }
}
