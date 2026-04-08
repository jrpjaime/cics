import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AdminService } from '../services/admin.service';
import { AlertService } from '../../../shared/services/alert.service';
import { LoaderService } from '../../../shared/services/loader.service';
import { ModalService } from '../../../shared/services/modal.service';

@Component({
  selector: 'app-usuario-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './usuario-detalle.component.html'
})
export class UsuarioDetalleComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private adminService = inject(AdminService);
  private alert = inject(AlertService);
  private loader = inject(LoaderService);
  private modalService = inject(ModalService);

  isEditMode = false;
  idUsuarioSeleccionado: number | null = null;
  cambiosRealizados = false;
  estaCargado = false;

  usuario: any = {};

  ngOnInit(): void {
    this.route.params.subscribe(params => {
        const id = params['id'];
        if (id) {
            this.isEditMode = true;
            this.idUsuarioSeleccionado = +id;
            this.cargarDetalle(this.idUsuarioSeleccionado);
        } else {
            this.prepararNuevo();
        }
    });
  }

  prepararNuevo() {
    this.usuario = {
        cveUsuarioApi: '',
        desPasswordApi: '',
        cveUsuarioMainframe: '',
        desPasswordMainframe: '',
        cveRol: '', // Inicializado vacío para forzar selección
        indActivo: 1,
        permisos: []
    };
    this.cambiosRealizados = false;
    this.estaCargado = true;
  }

  cargarDetalle(id: number) {
    this.loader.show();
    this.estaCargado = false;
    this.adminService.obtenerUsuarioPorId(id).subscribe({
      next: (data) => {
        this.usuario = {
          idUsuarioCics: data.ID_USUARIO_CICS,
          cveUsuarioApi: data.CVE_USUARIO_API,
          desPasswordApi: '',
          cveUsuarioMainframe: data.CVE_USUARIO_MAINFRAME,
          desPasswordMainframe: '',
          cveRol: data.CVE_ROL,
          indActivo: data.IND_ACTIVO,
          permisos: (data.permisos || []).map((p: any) => ({
            idPermisoCics: p.ID_PERMISO_CICS,
            nomPrograma: p.NOM_PROGRAMA,
            nomTransaccion: p.NOM_TRANSACCION,
            numTimeoutSec: p.NUM_TIMEOUT_SEC,
            indActivo: p.IND_ACTIVO
          }))
        };
        setTimeout(() => {
          this.estaCargado = true;
          this.cambiosRealizados = false;
          this.loader.hide();
        }, 300);
      },
      error: () => {
        this.loader.hide();
        this.alert.error("No se pudo cargar la información.");
        this.regresar();
      }
    });
  }

  registrarCambio() {
    if (this.estaCargado) this.cambiosRealizados = true;
  }

  toggleEstadoUsuario() {
    this.usuario.indActivo = (this.usuario.indActivo === 1) ? 0 : 1;
    this.registrarCambio();
  }

  onRolChange() {
    if (!this.estaCargado) return;

    // Si cambia de CLIENTE a ADMIN, advertir sobre la eliminación de programas
    if (this.usuario.cveRol === 'ADMIN' && this.isEditMode && this.usuario.permisos.some((p:any) => p.indActivo === 1)) {
        this.modalService.showDialog('confirm', 'warning', 'Atención',
            'Al cambiar a perfil ADMIN, se darán de baja los programas autorizados de Mainframe. ¿Desea continuar?',
            (conf: boolean) => {
                if (conf) {
                    this.usuario.permisos.forEach((p: any) => p.indActivo = 0);
                    this.usuario.cveUsuarioMainframe = null;
                    this.registrarCambio();
                    this.guardar();
                } else { this.usuario.cveRol = 'CLIENTE'; }
            }, 'Aceptar', 'Cancelar');
    } else {
        this.registrarCambio();
    }
  }

  agregarFilaPermiso() {
    this.usuario.permisos.push({ idPermisoCics: null, nomPrograma: '', nomTransaccion: '', numTimeoutSec: 30, indActivo: 1 });
    this.registrarCambio();
  }

  eliminarODesactivarPermiso(index: number) {
    const p = this.usuario.permisos[index];
    if (p.idPermisoCics) p.indActivo = (p.indActivo === 1) ? 0 : 1;
    else this.usuario.permisos.splice(index, 1);
    this.registrarCambio();
  }

  guardar() {
    // 1. Validar que el Rol haya sido seleccionado
    if (!this.usuario.cveRol) {
        this.alert.warn("Debe seleccionar un Rol del Sistema."); return;
    }

    // 2. Validar Usuario y Password API
    if (!this.usuario.cveUsuarioApi || (!this.isEditMode && !this.usuario.desPasswordApi)) {
      this.alert.warn("Los datos de Usuario y Contraseña del portal son obligatorios."); return;
    }

    // 3. Validaciones de Rol CLIENTE
    if (this.usuario.cveRol === 'CLIENTE') {
        if (!this.usuario.cveUsuarioMainframe || (!this.isEditMode && !this.usuario.desPasswordMainframe)) {
            this.alert.warn("Los datos RACF de Mainframe son obligatorios para perfil CLIENTE."); return;
        }
        if (this.usuario.permisos.some((p:any) => p.indActivo === 1 && (!p.nomPrograma || !p.nomTransaccion))) {
            this.alert.warn("Existen programas incompletos en la tabla."); return;
        }
    }

    this.loader.show();
    const op = this.isEditMode ? this.adminService.actualizarUsuario(this.usuario) : this.adminService.registrarUsuario(this.usuario);

    op.subscribe({
      next: () => {
        this.loader.hide();
        this.cambiosRealizados = false;
        this.alert.success("Datos guardados exitosamente.");
        setTimeout(() => this.regresar(), 1500);
      },
      error: (err) => {
        this.loader.hide();
        this.alert.error(err.error?.message || "Ocurrió un error en el servidor.");
      }
    });
  }

  regresar() { this.router.navigate(['/admin/usuarios']); }
}
