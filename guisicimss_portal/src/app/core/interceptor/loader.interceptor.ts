import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs';
import { LoaderService } from '../../shared/services/loader.service';

export const loaderInterceptor: HttpInterceptorFn = (req, next) => {
  const loaderService = inject(LoaderService);

  loaderService.show(); // Muestra el loader

  return next(req).pipe(
    finalize(() => {
      loaderService.hide(); // Oculta el loader cuando la petición finaliza
    })
  );
};
