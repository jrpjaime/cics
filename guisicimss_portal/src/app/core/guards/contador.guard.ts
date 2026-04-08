import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { SharedService } from '../../shared/services/shared.service';
import { Constants } from '../../global/Constants';
import { map, take } from 'rxjs/operators';
import { AlertService } from '../../shared/services/alert.service';

export const ContadorGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const sharedService = inject(SharedService);
  const router = inject(Router);
  const alertService = inject(AlertService);

  // Primero, que el usuario esté autenticado.
  if (!authService.isAuthenticated()) {
    router.navigate(['/login']);
    return false;
  }

  // verifica si tiene el rol "Contador".
  // se utiliza el `currentRoleSesion` del SharedService para obtener los roles del usuario loggeado.
  return sharedService.currentRoleSesion.pipe(
    take(1),
    map(roles => {

      if (roles && roles.includes(Constants.roleContador)) {
        return true;
      } else {
        alertService.error('No tienes los permisos necesarios para acceder a esta sección.');
        router.navigate(['/home']);
        return false;
      }
    })
  );
};
