import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SharedService } from '../../shared/services/shared.service';
import { AuthService } from '../services/auth.service';
import { AlertService } from '../../shared/services/alert.service';

export const AdminGuard: CanActivateFn = (route, state) => {
  const sharedService = inject(SharedService);
  const authService = inject(AuthService);
  const router = inject(Router);
  const alert = inject(AlertService);

  // 1. Asegurar que los datos del usuario estén cargados en el servicio
  sharedService.initializeUserData();

  const roles = sharedService.getRolesSync();

  if (roles.includes('ADMIN')) {
    return true; // Acceso permitido
  }

  // 2. Si no es ADMIN, bloqueo total
  alert.error("Acceso Denegado: Su perfil es de uso exclusivo para servicios API y no tiene privilegios para acceder a esta Consola de Administración.");
  authService.logout(); // Limpiamos sesión
  router.navigate(['/login']);
  return false;
};
