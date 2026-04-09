import { Routes } from '@angular/router';
import { LayoutComponent } from './shared/layout/layout.component';
import { HomeComponent } from './business/home/home.component';
import { LoginComponent } from './business/authentication/login/login.component';
import { AuthGuard } from './core/guards/auth.guard';
import { AuthenticatedGuard } from './core/guards/authenticated.guard';
import { AdminGuard } from './core/guards/admin.guard';

export const routes: Routes = [
  // 1. Ruta de Login: Bloqueada si ya hay una sesión activa
  {
    path: 'login',
    component: LoginComponent,
    canActivate: [AuthenticatedGuard]
  },

  // 2. Rutas Protegidas dentro del Layout Institucional
  {
    path: '',
    component: LayoutComponent,
    canActivate: [AdminGuard],
    children: [
      {
        path: 'home',
        component: HomeComponent
      },

      // --- MÓDULO DE ADMINISTRACIÓN DE SEGURIDAD HUB CICS ---

      // A. Pantalla de Listado (Filtros por Usuario API, Mainframe y Rol)
      {
        path: 'admin/usuarios',
        loadComponent: () => import('./business/admin/usuarios-admin/usuarios-admin.component')
          .then(m => m.UsuariosAdminComponent)
      },

      // B. Pantalla de Registro (Nuevo Usuario)
      {
        path: 'admin/usuarios/nuevo',
        loadComponent: () => import('./business/admin/usuario-detalle/usuario-detalle.component')
          .then(m => m.UsuarioDetalleComponent)
      },

      // C. Pantalla de Edición (Carga datos por ID, bloquea edición de clave API)
      {
        path: 'admin/usuarios/editar/:id',
        loadComponent: () => import('./business/admin/usuario-detalle/usuario-detalle.component')
          .then(m => m.UsuarioDetalleComponent)
      },

      // D. Gestión Global de Permisos de Programas
      {
        path: 'admin/permisos',
        loadComponent: () => import('./business/admin/permisos-admin/permisos-admin.component')
          .then(m => m.PermisosAdminComponent)
      },
      {
        path: 'admin/dashboard',
        loadComponent: () => import('./business/cics/monitoreo/monitoreo-dashboard/monitoreo-dashboard.component')
          .then(m => m.MonitoreoDashboardComponent)
      },

      // Redirección por defecto si la ruta está vacía
      { path: '', redirectTo: 'home', pathMatch: 'full' }
    ]
  },

  // 3. Comodín: Redirige al home cualquier URL no reconocida
  {
    path: '**',
    redirectTo: 'home'
  }
];
