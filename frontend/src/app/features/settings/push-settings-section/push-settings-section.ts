import { Component, OnInit, inject } from '@angular/core';
import { PushSettingsService } from './push-settings.service';

@Component({
  selector: 'app-push-settings-section',
  templateUrl: './push-settings-section.html',
  styleUrl: './push-settings-section.scss',
})
export class PushSettingsSection implements OnInit {
  readonly push = inject(PushSettingsService);

  ngOnInit(): void {
    void this.push.initialize();
  }

  protected enable(): void {
    void this.push.enable();
  }

  protected disable(): void {
    void this.push.disable();
  }
}