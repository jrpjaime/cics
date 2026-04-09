import { HttpInterceptorFn } from '@angular/common/http';

export const cacheInterceptor: HttpInterceptorFn = (req, next) => {
  // Solo aplicamos a métodos GET
  if (req.method === 'GET') {
    const noCacheReq = req.clone({
      setHeaders: {
        'Cache-Control': 'no-cache, no-store, must-revalidate',
        'Pragma': 'no-cache',
        'Expires': '0'
      }
    });
    return next(noCacheReq);
  }

  // Si no es GET, pasa la petición tal cual
  return next(req);
};